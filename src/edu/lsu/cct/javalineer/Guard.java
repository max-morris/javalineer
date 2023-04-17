package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

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

    final AtomicReference<GuardTask> next = new AtomicReference<>();

    public void runGuarded(Runnable r) {
        TreeSet<Guard> guardsHeld = new TreeSet<>();
        guardsHeld.add(this);
        runGuarded_(guardsHeld, r);
    }

    private void runGuarded_(TreeSet<Guard> guardsHeld, Runnable r) {
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
    public static void runGuarded(final Guard g1, Runnable r) {
        g1.runGuarded(r);
    }

    // Multiple guards
    public static void runGuarded(Runnable r, final Guard... guards) {
        if (guards.length == 0) {
            r.run();
        } else if (guards.length == 1) {
            guards[0].runGuarded(r);
        } else {
            TreeSet<Guard> ts = new TreeSet<>(Arrays.asList(guards));
            runGuarded(ts, r);
        }
    }

    public static void runGuarded(TreeSet<Guard> ts, Runnable r) {
        if(ts.size() == 1) {
            runGuarded(ts.first(), r);
            return;
        }
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
            lig.get(last).runGuarded_(guardsHeld, () -> {
                r.run();
                // last to run unlocks everything
                for (int i = 0; i < last; i++) {
                    guardTasks.get(i).free();
                }
            });
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

    public static CompletableFuture<Void> runCondition(final TreeSet<Guard> ts, final CondTask c) {
        assert ts.size() > 0;
        Cond cond = new Cond();
        cond.task = c;
        cond.gset = ts;
        for (Guard g : ts) {
            g.condManager.add(new CondLink(cond));
        }
        Guard.runGuarded(ts, c);
        return cond.task.fut;
    }

    public static <T> CompletableFuture<Void> runCondition(GuardVar<T> gv, final CondCheck1<T> c) {
        return Guard.runCondition(gv,new CondTask1<T>(c));
    }

    public static <T> CompletableFuture<Void> runCondition(GuardVar<T> gv, final CondTask1<T> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv);
        c.set1(gv.var);
        return runCondition(ts,c);
    }

    public static <T> void runGuarded(final GuardVar<T> g, final GuardTask1<T> c) {
        g.runGuarded(()-> c.run(g.var));
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

    public int getId() {
        return id;
    }

    //region Generated

    public static <T1, T2> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final CondCheck2<T1, T2> c) {
        return Guard.runCondition(gv1, gv2, new CondTask2<T1, T2>(c));
    }

    public static <T1, T2> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final CondTask2<T1, T2> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        c.set1(gv1.var);
        c.set2(gv2.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var));
    }

    public static <T1, T2> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var));
    }

    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final CondCheck3<T1, T2, T3> c) {
        return runCondition(gv1, gv2, gv3, new CondTask3<T1, T2, T3>(c));
    }

    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final CondTask3<T1, T2, T3> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var));
    }

    public static <T1, T2, T3> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var));
    }

    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final CondCheck4<T1, T2, T3, T4> c) {
        return runCondition(gv1, gv2, gv3, gv4, new CondTask4<T1, T2, T3, T4>(c));
    }

    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final CondTask4<T1, T2, T3, T4> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }

    public static <T1, T2, T3, T4> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final CondCheck5<T1, T2, T3, T4, T5> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, new CondTask5<T1, T2, T3, T4, T5>(c));
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final CondTask5<T1, T2, T3, T4, T5> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }

    public static <T1, T2, T3, T4, T5> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final CondCheck6<T1, T2, T3, T4, T5, T6> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, new CondTask6<T1, T2, T3, T4, T5, T6>(c));
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final CondTask6<T1, T2, T3, T4, T5, T6> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
    }

    public static <T1, T2, T3, T4, T5, T6> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final CondCheck7<T1, T2, T3, T4, T5, T6, T7> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, new CondTask7<T1, T2, T3, T4, T5, T6, T7>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final CondTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final CondCheck8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, new CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final CondTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final CondCheck9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, new CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final CondCheck10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, new CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final CondCheck11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, new CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final CondCheck12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, new CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final CondCheck13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, new CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final CondCheck14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, new CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final CondCheck15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, new CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        c.set15(gv15.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final CondCheck16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, new CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        c.set15(gv15.var);
        c.set16(gv16.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final CondCheck17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, new CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        c.set15(gv15.var);
        c.set16(gv16.var);
        c.set17(gv17.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final CondCheck18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, new CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        c.set15(gv15.var);
        c.set16(gv16.var);
        c.set17(gv17.var);
        c.set18(gv18.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardVar<T19> gv19,
            final CondCheck19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        return runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19, new CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>(c));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardVar<T19> gv19,
            final CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        ts.add(gv19);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        c.set10(gv10.var);
        c.set11(gv11.var);
        c.set12(gv12.var);
        c.set13(gv13.var);
        c.set14(gv14.var);
        c.set15(gv15.var);
        c.set16(gv16.var);
        c.set17(gv17.var);
        c.set18(gv18.var);
        c.set19(gv19.var);
        return Guard.runCondition(ts, c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardVar<T19> gv19,
            final GuardTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        ts.add(gv19);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
            final GuardVar<T11> gv11,
            final GuardVar<T12> gv12,
            final GuardVar<T13> gv13,
            final GuardVar<T14> gv14,
            final GuardVar<T15> gv15,
            final GuardVar<T16> gv16,
            final GuardVar<T17> gv17,
            final GuardVar<T18> gv18,
            final GuardVar<T19> gv19,
            final GuardTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        ts.add(gv5);
        ts.add(gv6);
        ts.add(gv7);
        ts.add(gv8);
        ts.add(gv9);
        ts.add(gv10);
        ts.add(gv11);
        ts.add(gv12);
        ts.add(gv13);
        ts.add(gv14);
        ts.add(gv15);
        ts.add(gv16);
        ts.add(gv17);
        ts.add(gv18);
        ts.add(gv19);
        Guard.runGuarded(ts, () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
    }

    //endregion
}
