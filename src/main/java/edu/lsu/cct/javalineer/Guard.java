package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        runGuarded_(GuardSet.of(this), r);
    }

    private void runGuarded_(GuardSet guardsHeld, Runnable r) {
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
            runGuarded(GuardSet.of(guards), r);
        }
    }

    public static void runGuarded(GuardSet gs, Runnable r) {
        if (gs.size() == 1) {
            runGuarded(gs.get(0), r);
            return;
        }

        assert gs.size() > 1;

        List<GuardTask> guardTasks = new ArrayList<>();
        for (Guard guard : gs) {
            guardTasks.add(new GuardTask(guard, gs));
        }

        int last = gs.size() - 1;
        assert guardTasks.size() == gs.size();

        // set up the next to last task
        guardTasks.get(last - 1).setRun(() -> {
            // set up the last task
            gs.get(last).runGuarded_(gs, () -> {
                r.run();
                // last to run unlocks everything
                for (int i = 0; i < last; i++) {
                    guardTasks.get(i).free();
                }
            });
        });

        // prior tasks, each calls the next
        for (int i = 0; i < last - 1; i++) {
            final int next = i + 1;
            final var guardTask = guardTasks.get(i);
            final var guardNext = gs.get(next);
            final var guardTaskNext = guardTasks.get(next);
            guardTask.setRun(() -> {
                guardNext.dummyRunGuarded(guardTaskNext);
            });
        }
        // kick the whole thing off
        gs.get(0).dummyRunGuarded(guardTasks.get(0));
    }

    public void signal() {
        condManager.signal();
    }

    public void signalAll() {
        condManager.signalAll();
    }

    public static CompletableFuture<Void> runCondition(final GuardSet gs, final CondTask c) {
        assert gs.size() > 0;
        Cond cond = new Cond();
        cond.task = c;
        cond.gSet = gs;
        for (Guard g : gs) {
            g.condManager.add(new CondLink(cond));
        }
        Guard.runGuarded(gs, c);
        return cond.task.fut;
    }

    public static CompletableFuture<Void> runCondition(final GuardSet gs, final CondCheck0 c) {
        return Guard.runCondition(gs, new CondTask0(c));
    }

    public static <T> CompletableFuture<Void> runCondition(GuardVar<T> gv, final CondCheck1<T> c) {
        return Guard.runCondition(gv, new CondTask1<T>(c));
    }

    public static <T> CompletableFuture<Void> runCondition(GuardVar<T> gv, final CondTask1<T> c) {
        c.set1(gv.var);
        return runCondition(GuardSet.of(gv), c);
    }

    public static <T> void runGuarded(final GuardVar<T> g, final GuardTask1<T> c) {
        g.runGuarded(() -> c.run(g.var));
    }

    public static boolean has(Guard g) {
        GuardSet gs = GuardTask.GUARDS_HELD.get();
        if (gs == null) {
            return false;
        }
        return gs.contains(g);
    }

    public static boolean has(Collection<Guard> guards) {
        GuardSet gs = GuardTask.GUARDS_HELD.get();
        if (gs == null) {
            return false;
        }
        return guards.stream().allMatch(Guard::has);
    }

    public int getId() {
        return id;
    }

    // region Generated

    public static <T1, T2> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final CondCheck2<T1, T2> c) {
        return Guard.runCondition(gv1, gv2,new CondTask2<T1, T2>(c));
    }
    public static <T1, T2> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final CondTask2<T1, T2> c) {
        c.set1(gv1.var);
        c.set2(gv2.var);
        return runCondition(GuardSet.of(gv1, gv2),c);
    }
    public static <T1, T2> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        Guard.runGuarded(GuardSet.of(gv1, gv2), () -> c.run(gv1.var, gv2.var));
    }

    public static <T1, T2> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2)), () -> c.run(gv1.var, gv2.var));
    }


    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final CondCheck3<T1, T2, T3> c) {
        return Guard.runCondition(gv1, gv2, gv3,new CondTask3<T1, T2, T3>(c));
    }
    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final CondTask3<T1, T2, T3> c) {
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3),c);
    }
    public static <T1, T2, T3> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3), () -> c.run(gv1.var, gv2.var, gv3.var));
    }

    public static <T1, T2, T3> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3)), () -> c.run(gv1.var, gv2.var, gv3.var));
    }


    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final CondCheck4<T1, T2, T3, T4> c) {
        return Guard.runCondition(gv1, gv2, gv3, gv4,new CondTask4<T1, T2, T3, T4>(c));
    }
    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final CondTask4<T1, T2, T3, T4> c) {
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4),c);
    }
    public static <T1, T2, T3, T4> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }

    public static <T1, T2, T3, T4> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }


    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final CondCheck5<T1, T2, T3, T4, T5> c) {
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5,new CondTask5<T1, T2, T3, T4, T5>(c));
    }
    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final CondTask5<T1, T2, T3, T4, T5> c) {
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5),c);
    }
    public static <T1, T2, T3, T4, T5> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }

    public static <T1, T2, T3, T4, T5> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }


    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final CondCheck6<T1, T2, T3, T4, T5, T6> c) {
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6,new CondTask6<T1, T2, T3, T4, T5, T6>(c));
    }
    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final CondTask6<T1, T2, T3, T4, T5, T6> c) {
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6),c);
    }
    public static <T1, T2, T3, T4, T5, T6> void runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
    }

    public static <T1, T2, T3, T4, T5, T6> void runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7,new CondTask7<T1, T2, T3, T4, T5, T6, T7>(c));
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
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8,new CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>(c));
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
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9,new CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>(c));
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
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        c.set5(gv5.var);
        c.set6(gv6.var);
        c.set7(gv7.var);
        c.set8(gv8.var);
        c.set9(gv9.var);
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10,new CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11,new CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12,new CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13,new CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14,new CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15,new CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16,new CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17,new CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18,new CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
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
        return Guard.runCondition(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19,new CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>(c));
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
        return runCondition(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19),c);
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
        Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
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
        var held = GuardTask.GUARDS_HELD.get();
        Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
    }
    // endregion
}
