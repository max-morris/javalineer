package edu.lsu.cct.javalineer;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public class GuardTask {
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
        }
        try {
            r.run();
        } catch (Throwable t) {
            // An exception must never escape from here: it would skip free_(), leaving the guard locked, or
            // break the hand-off loop in free_(), stranding every task queued behind this one. It would also
            // propagate into whatever unrelated code happens to be running this task. Tasks submitted through
            // Guard capture their own exceptions into their futures, so this is only a safety net.
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
     * {@link Guard#runGuarded(GuardSet, Runnable)}). Unlike {@link #free_()}, it never runs the successor on this
     * thread while the pool accepts tasks: a successor found here is handed to the pool. The release of an
     * acquisition must not wait for the work queued behind one of its guards, which may never end (a queue whose
     * every task enqueues another on the same guard), while its later guards are still held.
     */
    void release() {
        assert isDummyTask : "Calling GuardTask.release() on a user task.";
        var ok = guard.locked.compareAndSet(true, false);
        assert ok;
        if (next.compareAndSet(null, DONE)) {
            return;
        }
        // The CAS failed, so a successor linked itself behind this task and this thread alone must run it.
        final GuardTask gt = next.get();
        try {
            Pool.run(gt::handOff_);
        } catch (RejectedExecutionException e) {
            // The pool was shut down or swapped. Running the successor inline is always safe; it only delays the
            // release of this acquisition's remaining guards until the successor's chain of handoffs ends.
            // Skipping it would strand every task queued on this guard.
            gt.handOff_();
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
            var n = gt.next;
            if (n.compareAndSet(null, DONE)) {
                return;
            }
            gt = n.get();
        }
    }

    private void free_() {
        var ok = guard.locked.compareAndSet(true, false);
        assert ok;
        var n = next;
        while (!n.compareAndSet(null, DONE)) {
            final GuardTask gt = n.get();
            gt.run_();
            if (gt.isDummyTask()) {
                return;
            }
            ok = gt.guard.locked.compareAndSet(true, false);
            assert ok;
            n = gt.next;
        }
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
