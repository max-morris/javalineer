package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.TreeSet;
import java.util.Set;

public class GuardTask {
    final static AtomicInteger nextId = new AtomicInteger(-1);

    final static GuardTask DONE = new GuardTask(null, () -> {
        assert false : "DONE should not be executed";
    }, null);


    final static ThreadLocal<Set<Guard>> GUARDS_HELD = new ThreadLocal<>();

    final int id = nextId.getAndIncrement();
    final AtomicReference<GuardTask> next = new AtomicReference<>();
    final Guard guard;
    final TreeSet<Guard> guardsHeld;

    private final boolean isDummyTask;
    private Runnable r;

    public GuardTask(Guard g, TreeSet<Guard> guardsHeld) {
        this.guard = g;
        this.isDummyTask = true;
        this.guardsHeld = guardsHeld;
    }

    public GuardTask(Guard g, Runnable r, TreeSet<Guard> guardsHeld) {
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
        assert guard.locked.compareAndSet(false, true) : String.format("%s %d %d", this, ThreadID.get(), guardsHeld.size());
        if (isUserTask()) {
            GUARDS_HELD.set(guardsHeld);
            for (Guard g : guardsHeld)
                assert g.locked.get();
        }
        Run.run(r);
    }

    public void run() {
        run_();
        if (isUserTask())
            free_();
    }

    public void free() {
        assert isDummyTask : "Calling GuardTask.free() on a dummy task.";
        free_();
    }

    private void free_() {
        assert guard.locked.compareAndSet(true, false);
        var n = next;
        while (!n.compareAndSet(null, DONE)) {
            final GuardTask gt = n.get();
            gt.run_();
            if (gt.isDummyTask()) {
                return;
            }
            assert gt.guard.locked.compareAndSet(true, false);
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
