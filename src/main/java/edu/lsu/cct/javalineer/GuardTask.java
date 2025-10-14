package edu.lsu.cct.javalineer;

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
        } catch (RuntimeException | Error ex) {
            ex.printStackTrace();
            throw ex;
        } catch(Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void run() {
        run_();
        if (isUserTask())
            free_();
    }

    void free() {
        free(false);
    }

    void free(boolean forcePool) {
        assert isDummyTask : "Calling GuardTask.free() on a dummy task.";
        if (forcePool) {
            Pool.run(this::free_);
        } else {
            free_();
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
