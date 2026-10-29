package edu.lsu.cct.javalineer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A node of a guard's task queue. A user task (one single-guard {@link Guard#runGuarded(Runnable)}) is also the
 * future that call returns: run_ completes it when the task's runnable returns, or completes it exceptionally if the
 * runnable throws, before the guard is released. That saves the separate future and the capturing wrapper which
 * would otherwise be allocated per operation and kept alive in the queue. The queue machinery reads only r, next,
 * guard, guardsHeld and isDummyTask, never the future's state, so a caller who completes, cancels or obtrudes the
 * returned future changes only what the future reports, as with a separate future. Dummy tasks (the nodes of a
 * multi-guard acquisition) are never completed; the multi-guard path returns a future of its own.
 */
public class GuardTask extends CompletableFuture<Void> {
    final static AtomicInteger nextId = new AtomicInteger(-1);

    final static GuardTask DONE = new GuardTask(null, () -> {
        assert false : "DONE should not be executed";
    }, null);


    final static ThreadLocal<GuardSet> GUARDS_HELD = new ThreadLocal<>();

    final int id = nextId.getAndIncrement();
    final AtomicReference<GuardTask> next = new AtomicReference<>();
    final Guard guard;
    final GuardSet guardsHeld;

    private final boolean isDummyTask;
    private Runnable r;

    public GuardTask(Guard g, GuardSet guardsHeld) {
        this.guard = g;
        this.isDummyTask = true;
        this.guardsHeld = guardsHeld;
    }

    public GuardTask(Guard g, Runnable r, GuardSet guardsHeld) {
        this.guard = g;
        this.isDummyTask = false;
        this.r = r;
        this.guardsHeld = guardsHeld;
    }

    public void setRun(Runnable r) {
        assert this.r == null;
        this.r = r;
    }

    private void run_() {
        int id = ThreadID.get();
        var ok = guard.locked.compareAndSet(false, true);
        assert ok : String.format("%s %d %d", this, ThreadID.get(), guardsHeld.size());
        if (isUserTask()) {
            GUARDS_HELD.set(guardsHeld);
            for (Guard g : guardsHeld)
                assert g.locked.get();
            // The task is its own future (as Pool.runCapturing does for a separate one): completed normally if r
            // returns, exceptionally (after reporting to the failure handler) if r throws. Either way nothing
            // escapes, and the future's dependents run here, on this thread, while the guard is still held.
            // The runnable and the completion (with the dependents it runs) are the body: counted, so that free_
            // knows when it runs inside one (as the separate future's completion was, inside the runnable).
            Trampoline tr = Trampoline.enterBody();
            try {
                Throwable failure = null;
                try {
                    r.run();
                } catch (Throwable t) {
                    failure = t;
                }
                // A caller may keep the future (this task) long after it ran; it must not keep r and what r
                // captured. run_ runs once per task, so r is never read again.
                r = null;
                try {
                    if (failure == null) {
                        complete(null);
                    } else {
                        Pool.reportTaskFailure(failure);
                        completeExceptionally(failure);
                    }
                } catch (Throwable t) {
                    // Not expected (complete runs dependents but captures what they throw); see below.
                    Pool.reportTaskFailure(t);
                }
            } finally {
                tr.bodies--;
            }
            return;
        }
        try {
            r.run();
        } catch (Throwable t) {
            // An exception must never escape from here: it would skip free_(), leaving the guard locked, or
            // break the hand-off loop in free_(), stranding every task queued behind this one. It would also
            // propagate into whatever unrelated code happens to be running this task. A dummy task's runnable
            // enqueues the next node, or runs a multi-guard body through Pool.runCapturing, so this is only a
            // safety net.
            Pool.reportTaskFailure(t);
        }
    }

    public void run() {
        run_();
        if (isUserTask())
            free_();
    }

    /**
     * Releases this dummy task's guard, part of the release of a multi-guard acquisition (see
     * {@link #releaseAll(List)}): clears the guard and CASes next from null to DONE, as {@link #free_()} does,
     * but does not run the successor.
     *
     * @return The successor linked behind this task, which this thread alone must now run (or hand to the pool),
     *         or null if there is none.
     */
    private GuardTask release() {
        assert isDummyTask : "Calling GuardTask.release() on a user task.";
        var ok = guard.locked.compareAndSet(true, false);
        assert ok;
        return takeNext();
    }

    /**
     * The release's step on next, after the guard is cleared: sets next to DONE and returns what it held. If that
     * was null, there was no successor, and an enqueuer which later finds this task as its predecessor fails its
     * CAS from null and runs its own task (as with a CAS of next from null to DONE). Otherwise a successor had
     * linked itself behind this task, and this thread alone must now run it (or hand it on). That successor's
     * enqueuer was the only one ever given this task as its predecessor, and it has finished with next, so no one
     * reads next again: overwriting it with DONE, instead of leaving the successor there as a failed CAS would, is
     * invisible to the queue. It drops the link, so that a caller keeping this task's future does not keep alive
     * every task queued on the guard after it.
     *
     * @return The successor, or null if there is none.
     */
    private GuardTask takeNext() {
        GuardTask succ = next.getAndSet(DONE);
        assert succ != DONE : "A task was released twice.";
        return succ;
    }

    /**
     * Releases every guard of a finished multi-guard acquisition, whose tasks (all dummies, one per guard, in guard
     * order) are given. No successor runs on this thread until every guard is released: the release of an
     * acquisition must not wait for the work queued behind one of its guards, which may never end (a queue whose
     * every task enqueues another on the same guard), while its later guards are still held (claims review F5 (b)).
     * The first successor found is then offered to this thread's {@link Trampoline}; any other is handed to the
     * pool.
     */
    static void releaseAll(List<GuardTask> acquisition) {
        GuardTask kept = null;
        for (int i = 0; i < acquisition.size(); i++) {
            GuardTask succ = acquisition.get(i).release();
            if (succ != null) {
                if (kept == null) {
                    kept = succ;
                } else {
                    Trampoline.submit(succ);
                }
            }
        }
        if (kept != null) {
            Trampoline.offer(kept, acquisition);
        }
    }

    /**
     * A per-thread loop which runs, one at a time and without growing the stack, the successors left by the
     * releases of multi-guard acquisitions. In a convoy of acquisitions of the same guards, the release of each
     * finds the next one queued behind it. Running that inline at the release would nest every acquisition's body
     * inside the previous one's (unbounded recursion), and handing each to the pool costs a pool hop per
     * acquisition. Instead a loop runs one handoff at a time; the release of the acquisition which that handoff
     * started (on the same thread, before the handoff returns) puts its own successor in the loop's slot, so the
     * convoy runs on one thread at one pool hop per run of the loop, not per acquisition.
     * <p>
     * A loop only ever runs at the bottom of a pool task. Started inline, at a release with no loop on the thread,
     * it would run below whatever called that code: a task which called runGuarded, its acquisition having run
     * inline, would not return while the convoy (or a self-perpetuating queue) behind the acquisition went on, and
     * that task's guards would stay held.
     */
    static final class Trampoline {
        private static final ThreadLocal<Trampoline> CURRENT = ThreadLocal.withInitial(Trampoline::new);

        /** The successor this loop is running, or null if no loop runs on this thread. */
        private GuardTask running;
        /** The successor this loop runs next, or null. */
        private GuardTask slot;
        /**
         * The number of bodies running on this thread (nested acquisitions run their bodies inline inside their
         * caller's). Positive means that whatever runs now runs inside the body of a task that holds guards.
         */
        int bodies;

        /** Whether this thread is running the body of a guarded task (see {@link GuardTask#free_()}). */
        static boolean inBody() {
            return CURRENT.get().bodies > 0;
        }

        /** Counts a body starting on this thread; the caller decrements bodies of the result when it ends. */
        static Trampoline enterBody() {
            Trampoline tr = CURRENT.get();
            tr.bodies++;
            return tr;
        }

        /**
         * Runs succ, a successor left by the release of the acquisition whose tasks are given, after that release.
         * If this thread is running a trampoline loop, the loop's current successor started this acquisition (so
         * that handoff returns to the loop as soon as this release does: a free_ loop stops after a dummy task),
         * and the loop's slot is free, succ goes into the slot. Otherwise succ goes to the pool, as a loop of its
         * own: this thread may have nothing to return to (no loop), or a user task's handoff, which can go on for
         * as long as tasks keep being queued on its guard.
         */
        static void offer(GuardTask succ, List<GuardTask> acquisition) {
            Trampoline tr = CURRENT.get();
            if (tr.running != null && tr.slot == null && contains(acquisition, tr.running)) {
                tr.slot = succ;
            } else {
                submit(succ);
            }
        }

        private static boolean contains(List<GuardTask> acquisition, GuardTask t) {
            for (int i = 0; i < acquisition.size(); i++) {
                if (acquisition.get(i) == t) {
                    return true;
                }
            }
            return false;
        }

        /** Hands succ to the pool, as a trampoline loop of its own. */
        static void submit(GuardTask succ) {
            try {
                Pool.run(() -> {
                    Trampoline tr = CURRENT.get();
                    if (tr.running == null) {
                        tr.loop(succ);
                    } else {
                        // An executor which runs a submission synchronously (DebugPool, called outside its loop)
                        // can run this inside another loop on the same thread.
                        succ.handOff_();
                    }
                });
            } catch (RejectedExecutionException e) {
                // The pool was shut down or swapped. Running the successor inline is always safe; when this is
                // called from releaseAll it only delays the release of the acquisition's remaining guards until the
                // successor's chain of handoffs ends. Skipping it would strand every task queued on its guard.
                succ.handOff_();
            }
        }

        private void loop(GuardTask succ) {
            try {
                while (succ != null) {
                    running = succ;
                    slot = null;
                    succ.handOff_();
                    succ = slot;
                }
            } finally {
                running = null;
                slot = null;
            }
        }
    }

    /**
     * Runs this task, which has been handed its guard by the release of its predecessor, and continues the
     * {@link #free_()} handoff loop from it.
     */
    private void handOff_() {
        GuardTask gt = this;
        while (true) {
            gt.run_();
            if (gt.isDummyTask()) {
                return;
            }
            var ok = gt.guard.locked.compareAndSet(true, false);
            assert ok;
            gt = gt.takeNext();
            if (gt == null) {
                return;
            }
        }
    }

    /**
     * Releases this single-guard user task's guard and runs the tasks queued behind it, one after the other, until
     * the queue is empty or it reaches a dummy task. Called only by {@link #run()}, that is, where runGuarded ran this
     * task inline on its caller's thread.
     * <p>
     * If that caller is itself the body of a guarded task (an acquisition nested inside another), the successor is
     * not run here but handed to the pool, where it starts a {@link Trampoline} loop: the caller's guards are held
     * until it returns, and the queue behind this guard may never end (each of its tasks enqueuing another on the
     * same guard), so running it here could hold them forever (Nest1, the single-guard form of claims review
     * F5 (b)). The rule is the trampoline's: successors run inline only at the bottom of a pool task, or at top
     * level, where no guard is held below them. At top level (no body on this thread) the loop is unchanged, so a
     * convoy on one guard still runs inline; the nested acquisition itself still ran inline, and only the run of
     * its successor moves, and only when there is one.
     */
    private void free_() {
        var ok = guard.locked.compareAndSet(true, false);
        assert ok;
        GuardTask gt = takeNext();
        if (gt == null) {
            return;
        }
        if (Trampoline.inBody()) {
            Trampoline.submit(gt);
            return;
        }
        do {
            gt.run_();
            if (gt.isDummyTask()) {
                return;
            }
            ok = gt.guard.locked.compareAndSet(true, false);
            assert ok;
            gt = gt.takeNext();
        } while (gt != null);
    }

    public String toString() {
        return "gt[" + id + "," + guard + "," + next + "]";
    }

    public boolean isUserTask() {
        return !isDummyTask;
    }

    public boolean isDummyTask() {
        return isDummyTask;
    }
}
