package edu.lsu.cct.javalineer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * A node of a guard's task queue. A user task (one single-guard {@link Guard#runGuarded(Runnable)}) is also the
 * future that call returns: run_ completes it when the task's runnable returns, or completes it exceptionally if the
 * runnable throws, before the guard is released. That saves the separate future and the capturing wrapper which
 * would otherwise be allocated per operation and kept alive in the queue. The queue machinery reads only r, next,
 * guard, guardsHeld and isDummyTask, never the future's state, so a caller who completes, cancels or obtrudes the
 * returned future changes only what the future reports, as with a separate future. Dummy tasks (the nodes of a
 * multi-guard acquisition) are never completed; the multi-guard path returns a future of its own.
 * <p>
 * Not public API: the class, its constructors, {@link #run()} and {@link #setRun(Runnable)} are package-private, so
 * code outside this package sees a task only as the {@code CompletableFuture<Void>} that runGuarded returns. It
 * cannot name the class, so it cannot cast the future back to a task and run it, or give it a new runnable: a
 * task runs once, and only when the queue hands it its guard (runGuarded_, free_, handOff_). Only the future's
 * own methods are reachable, and those affect only what the future reports (above).
 */
final class GuardTask extends CompletableFuture<Void> {
    /**
     * The link to the successor: null, then the successor or {@link #DONE}, then DONE for good. Every write goes
     * through {@link #NEXT}: the enqueuer's compareAndSet from null to the successor ({@link #link}), the release's
     * compareAndSet from null to DONE when it finds no successor, and its setRelease of DONE over a successor it
     * found ({@link #takeNext}). The plain reads are volatile reads: link's check for DONE before its CAS, and
     * takeNext's read of a linked successor (and its read again after a failed CAS), each of which synchronizes with
     * the write it sees (see there); also toString. A field of the task itself rather than a separate
     * AtomicReference, so the queue costs one object (and one cache line) less per task.
     */
    private volatile GuardTask next;
    private static final VarHandle NEXT;

    static {
        try {
            NEXT = MethodHandles.lookup().findVarHandle(GuardTask.class, "next", GuardTask.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final static GuardTask DONE = new GuardTask(null, () -> {
        assert false : "DONE should not be executed";
    }, null);


    /**
     * The guards held by the body running on this thread, or null outside a body. Kept in the thread's
     * {@link Trampoline}, not in a ThreadLocal of its own: a ThreadLocal's set writes its map entry, a small object
     * that GC compaction may place on the same cache line as other threads' hot state, and every task sets it twice.
     * The trampoline is padded (see there), and one lookup serves both.
     */
    static final class Held {
        GuardSet get() {
            return Trampoline.current().held;
        }

        void set(GuardSet gs) {
            Trampoline.current().held = gs;
        }
    }

    final static Held GUARDS_HELD = new Held();

    final Guard guard;
    final GuardSet guardsHeld;

    private final boolean isDummyTask;
    private Runnable r;

    GuardTask(Guard g, GuardSet guardsHeld) {
        this.guard = g;
        this.isDummyTask = true;
        this.guardsHeld = guardsHeld;
    }

    GuardTask(Guard g, Runnable r, GuardSet guardsHeld) {
        this.guard = g;
        this.isDummyTask = false;
        this.r = r;
        this.guardsHeld = guardsHeld;
    }

    void setRun(Runnable r) {
        assert this.r == null;
        this.r = r;
    }

    /**
     * Asserts that every guard of {@code gs} is locked. Guard.locked is written only inside assertions in this class, so
     * it must also be read under this class's assertion status: a read in another class could run with assertions
     * enabled there and disabled here (per-class -ea flags), see the flag never set, and fail an acquisition that is
     * in fact held.
     */
    static void assertLocked(GuardSet gs) {
        for (Guard g : gs)
            assert g.locked.get();
    }

    private void run_() {
        // Guard.locked is a diagnostic, kept only when assertions are enabled (see there).
        assert guard.locked.compareAndSet(false, true) : String.format("%s %d %d", this, ThreadID.get(), guardsHeld.size());
        // The guards held by whatever this thread was running: a body this one is nested in, or none. Restored when
        // the body ends, so that an enclosing body goes on seeing its own guards, and a thread that runs no body sees
        // none.
        GuardSet outerHeld = null;
        if (isUserTask()) {
            // One thread-local lookup for the held guards and the body count.
            Trampoline tr = Trampoline.current();
            outerHeld = tr.held;
            tr.held = guardsHeld;
            assertLocked(guardsHeld);
            // The task is its own future (as Pool.runCapturing does for a separate one): completed normally if r
            // returns, exceptionally (after reporting to the failure handler) if r throws. Either way nothing
            // escapes, and the future's dependents run here, on this thread, while the guard is still held.
            // The runnable and the completion (with the dependents it runs) are the body: counted, so that free_
            // knows when it runs inside one (as the separate future's completion was, inside the runnable).
            tr.bodies++;
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
                // After the completion, so that the future's dependents, which run inside it, still see the body's
                // guards.
                tr.held = outerHeld;
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

    void run() {
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
        assert guard.locked.compareAndSet(true, false);
        return takeNext();
    }

    /**
     * The enqueuer's step on next (Guard.runGuarded_, Guard.dummyRunGuarded): links succ behind this task, its
     * predecessor, by a CAS of next from null to succ. If it fails, this task has already been released (next is
     * DONE) and the enqueuer must run succ itself.
     */
    boolean link(GuardTask succ) {
        // DONE is final: once released, next never changes again, so a read that sees it is the CAS's failure
        // without the atomic step (an uncontended acquisition finds its predecessor released). The read
        // synchronizes with the release's CAS from null to DONE, as the failed CAS would. A release which found a
        // successor also writes DONE, but then no enqueuer is left to call link on this task.
        if (next == DONE) {
            return false;
        }
        return NEXT.compareAndSet(this, null, succ);
    }

    /**
     * The release's step on next, after the guard is cleared: sets next to DONE and returns what it held. If that
     * was null, there was no successor, and an enqueuer which later finds this task as its predecessor fails its
     * CAS from null and runs its own task. Otherwise a successor had linked itself behind this task, and this thread
     * alone must now run it (or hand it on).
     * <p>
     * The step is a read first, and an atomic step only when the read finds no successor. A successor, once linked,
     * is there for good: the only enqueuer ever given this task as its predecessor has finished with next, and only
     * this release writes next after it. So a read that finds one has the same effect as the getAndSet(DONE) it
     * replaces, and the happens-before from the enqueuer comes from the read (a volatile read of the value its CAS
     * wrote). When the read finds null, a CAS from null to DONE decides the race with an enqueuer, as the getAndSet
     * did; if it fails, the enqueuer's CAS won and its node is the successor. Overwriting a found successor with DONE
     * is invisible to the queue, since no one reads next again; it drops the link, so that a caller keeping this
     * task's future does not keep alive every task queued on the guard after it. That write is a release store, not
     * an atomic step: with a backlog behind the guard, the drainer's release of each task needs no atomic
     * read-modify-write (the completion of the task's future is its only one).
     *
     * @return The successor, or null if there is none.
     */
    private GuardTask takeNext() {
        GuardTask succ = next;
        if (succ == null) {
            if (NEXT.compareAndSet(this, null, DONE)) {
                return null;
            }
            succ = next;
        }
        assert succ != DONE : "A task was released twice.";
        NEXT.setRelease(this, DONE);
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
    /**
     * Padding before a {@link Trampoline}'s fields: an int (filling the gap after the object header, so that no
     * subclass field can be placed there) and eight longs, 68 bytes. Superclass fields come first in the layout.
     */
    abstract static class TrampolinePad {
        int pi;
        long p0, p1, p2, p3, p4, p5, p6, p7;
    }

    /**
     * A thread's guard state, written by that thread on every task it runs (bodies twice, held twice): between
     * {@link TrampolinePad} and the longs of {@link Trampoline}, so that no other object shares a cache line with
     * these fields, whatever a GC compaction puts next to the trampoline (other threads' trampolines, their
     * thread-local maps). Without the padding a 40-byte trampoline next to another thread's state is false sharing
     * between whichever threads hold guards in turn.
     */
    abstract static class TrampolineState extends TrampolinePad {
        /** The successor this loop is running, or null if no loop runs on this thread. */
        GuardTask running;
        /** The successor this loop runs next, or null. */
        GuardTask slot;
        /**
         * The number of bodies running on this thread (nested acquisitions run their bodies inline inside their
         * caller's). Positive means that whatever runs now runs inside the body of a task that holds guards.
         */
        int bodies;
        /** The guards held by the body running on this thread, or null (see {@link GuardTask#GUARDS_HELD}). */
        GuardSet held;
        /**
         * How many fresh stacks this trampoline is lent to ({@link Trampoline#lend}): positive only while a call made
         * by {@link Guard#callOnFreshStack} runs, and then only the innermost such call's stack can touch it.
         */
        int lent;
    }

    static final class Trampoline extends TrampolineState {
        private static final ThreadLocal<Trampoline> CURRENT = ThreadLocal.withInitial(Trampoline::new);

        /** Padding after the fields (see {@link TrampolineState}). */
        long q0, q1, q2, q3, q4, q5, q6, q7;

        /** This thread's trampoline. */
        static Trampoline current() {
            return CURRENT.get();
        }

        /**
         * Makes tr, the trampoline of a thread now waiting in {@link Guard#callOnFreshStack}, this thread's for the
         * call (see {@link FreshStack}): this thread is a fresh stack's helper, between calls, with no trampoline of
         * its own in use. The object itself is lent, not a copy, so everything the call does to it (a body's held
         * guards, the count of bodies, a successor offered to the slot) is there for the caller when the call
         * returns, exactly as if the call had run on the caller's own stack.
         */
        static void lend(Trampoline tr) {
            CURRENT.set(tr);
            tr.lent++;
        }

        /** Ends {@link #lend}: the helper no longer has tr, which goes back to its waiting owner. */
        static void giveBack(Trampoline tr) {
            // The check fires after the trampoline is given back, never instead of it: an assertion that left lent
            // counted would make every later Pool.join of the owner refuse to wait.
            try {
                assert CURRENT.get() == tr : "a fresh stack's trampoline was replaced during the call";
            } finally {
                tr.lent--;
                CURRENT.remove();
            }
        }

        /**
         * Whether this thread is running a call on a fresh stack (see {@link Pool#join}). Here rather than in
         * {@link FreshStack}, so that a check needn't initialize that class (and read its stack size setting).
         */
        static boolean onFreshStack() {
            return CURRENT.get().lent > 0;
        }

        /** Whether this thread is running the body of a guarded task (see {@link GuardTask#free_()}). */
        static boolean inBody() {
            return CURRENT.get().bodies > 0;
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
                        // can run this inside another loop on the same thread. Unbounded (no J1 flush): a flush here
                        // would submit synchronously again, one level deeper per flush.
                        succ.handOff_(Long.MAX_VALUE);
                    }
                });
            } catch (RejectedExecutionException e) {
                // The pool was shut down or swapped. Running the successor inline is always safe; when this is
                // called from releaseAll it only delays the release of the acquisition's remaining guards until the
                // successor's chain of handoffs ends. Skipping it would strand every task queued on its guard.
                // Unbounded (no J1 flush): a flush would only meet the same rejection.
                succ.handOff_(Long.MAX_VALUE);
            }
        }

        /**
         * The trampoline loop. With bounded drains on (J1, {@link Pool#setDrainBound(int)}), the loop and the handoffs
         * it runs share one budget of B + 1 task runs, counting every run_ the loop starts: its own first item (which
         * therefore always runs) and at most B successors after it. Once the budget is spent, a successor still
         * pending, found by a handoff ({@link #handOff_(long)}) or left in the slot, is handed back to the pool and the
         * loop returns. So every drain runs its own item plus at most B successors, here as in the top-level drain of
         * {@link #free_()}.
         */
        private void loop(GuardTask succ) {
            // B + 1: the loop's own item, then at most B successors.
            long left = Pool.loopBudget();
            try {
                while (succ != null) {
                    running = succ;
                    slot = null;
                    left = succ.handOff_(left);
                    succ = slot;
                    if (succ != null && left <= 0) {
                        // J1 flush point (loop): the budget is spent and the slot holds the next successor.
                        slot = null;
                        submit(succ);
                        succ = null;
                    }
                }
            } finally {
                running = null;
                slot = null;
            }
        }
    }

    /**
     * Runs this task, which has been handed its guard by the release of its predecessor, and continues the
     * {@link #free_()} handoff loop from it, running at most {@code left} tasks in all (J1, bounded drains). If the
     * budget runs out while a successor is pending, the successor is handed to the pool (a trampoline loop of its
     * own) and this returns.
     *
     * @param left The number of task runs this drain may still make; {@code Long.MAX_VALUE} for unbounded.
     * @return What is left of the budget.
     */
    private long handOff_(long left) {
        GuardTask gt = this;
        while (true) {
            gt.run_();
            left--;
            if (gt.isDummyTask()) {
                return left;
            }
            assert gt.guard.locked.compareAndSet(true, false);
            gt = gt.takeNext();
            if (gt == null) {
                return left;
            }
            if (left <= 0) {
                // J1 flush point (handoff): released, successor found, budget spent.
                Trampoline.submit(gt);
                return left;
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
        assert guard.locked.compareAndSet(true, false);
        GuardTask gt = takeNext();
        if (gt == null) {
            return;
        }
        if (Trampoline.inBody()) {
            Trampoline.submit(gt);
            return;
        }
        // J1: this drain runs at most B successors inline after the caller's own task (whose run_ preceded this call
        // and is not counted), then hands the next one back to the pool, so that a caller of runGuarded returns after
        // its own body plus at most B successors.
        long left = Pool.drainBudget();
        do {
            gt.run_();
            left--;
            if (gt.isDummyTask()) {
                return;
            }
            assert gt.guard.locked.compareAndSet(true, false);
            gt = gt.takeNext();
            if (gt != null && left <= 0) {
                // J1 flush point (top-level free_): released, successor found, budget spent.
                Trampoline.submit(gt);
                return;
            }
        } while (gt != null);
    }

    public String toString() {
        // An identity hash, not a sequence number: a global counter would be one more cache line that every
        // issuing core writes on every operation, for the sake of this method alone.
        return "gt[" + Integer.toHexString(System.identityHashCode(this)) + "," + guard + "," + next + "]";
    }

    boolean isUserTask() {
        return !isDummyTask;
    }

    boolean isDummyTask() {
        return isDummyTask;
    }
}
