package edu.lsu.cct.javalineer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;

public class Guard implements Comparable<Guard> {
    final static AtomicInteger nextId = new AtomicInteger(0);
    final AtomicBoolean locked = new AtomicBoolean(false);
    final int id = nextId.getAndIncrement();

    CondManager condManager = new CondManager();

    public String toString() {
        return "g[" + id + "," + (locked.get() ? "T" : "F") + "]";
    }

    public int compareTo(Guard g) {
        return this.id - g.id;
    }

    final AtomRef<GuardTask> next = new AtomRef<>();

    public void runGuarded(Runnable r) {
        TreeSet<Guard> guardsHeld = new TreeSet<>();
        guardsHeld.add(this);
        runGuarded_(r, guardsHeld);
    }

    private void runGuarded_(Runnable r, TreeSet<Guard> guardsHeld) {
        GuardTask gTask = new GuardTask(this, r, guardsHeld);
        assert gTask.isUserTask();
        var prev = next.getAndSet(gTask);
        if (prev == null) {
            gTask.run();
        } else {
            if (!prev.next.compareAndSet(null, gTask)) {
                gTask.run();
            }
        }
    }

    private void dummyRunGuarded(GuardTask gTask) {
        assert gTask.isDummyTask();
        var prev = next.getAndSet(gTask);
        if (prev == null) {
            gTask.run();
        } else {
            if (!prev.next.compareAndSet(null, gTask)) {
                gTask.run();
            }
        }
    }

    // Single guard
    public static void runGuarded(Runnable r, final Guard g1) {
        g1.runGuarded(r);
    }

    // Multiple guards
    public static void runGuarded(Runnable r, final Guard... guards) {
        if (guards.length == 0) {
            Run.run(r);
        } else if (guards.length == 1) {
            guards[0].runGuarded(r);
        } else {
            TreeSet<Guard> ts = new TreeSet<>(Arrays.asList(guards));
            runGuarded(r, ts);
        }
    }

    public static void runGuarded(Runnable r, TreeSet<Guard> ts) {
        assert ts.size() > 1;

        List<Guard> lig = new ArrayList<>(ts);

        assert lig.size() == ts.size();

        TreeSet<Guard> guardsHeld = new TreeSet<>();
        guardsHeld.addAll(ts);

        List<GuardTask> guardTasks = new ArrayList<>();
        for (Guard guard : lig) {
            guardTasks.add(new GuardTask(guard, guardsHeld));
        }

        int last = lig.size() - 1;
        assert guardTasks.size() == ts.size();

        // set up the next to last task
        guardTasks.get(last - 1).setRun(() -> {
            // set up the last task
            lig.get(last).runGuarded_(() -> {
                Run.run(r);
                // last to run unlocks everything
                for (int i = 0; i < last; i++) {
                    guardTasks.get(i).free();
                }
            }, guardsHeld);
        });

        // prior tasks, each calls the next
        for (int i = 0; i < last - 1; i++) {
            final int step = i;
            final int next = i + 1;
            final var guardTask = guardTasks.get(i);
            final var guardNext = lig.get(next);
            final var guardTaskNext = guardTasks.get(next);
            guardTask.setRun(() -> {
                guardNext.dummyRunGuarded(guardTaskNext);
            });
        }
        // kick the whole thing off
        lig.get(0).dummyRunGuarded(guardTasks.get(0));
    }

    public void signal() {
        condManager.signal();
    }

    public void signalAll() {
        condManager.signalAll();
    }

    public static boolean has(Guard g) {
        java.util.Set<Guard> ts = GuardTask.GUARDS_HELD.get();
        if (ts == null) {
            return false;
        }
        return ts.contains(g);
    }

    public static boolean has(TreeSet<Guard> guards) {
        java.util.Set<Guard> ts = GuardTask.GUARDS_HELD.get();
        if (ts == null) {
            return false;
        }
        return ts.containsAll(guards);
    }
}
