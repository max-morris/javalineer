package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public class Guard implements Comparable<Guard> {
    final static AtomicInteger nextId = new AtomicInteger(0);
    final AtomicBoolean locked = new AtomicBoolean(false);
    final int id = nextId.getAndIncrement();

    public String toString() {
        return "g[" + id + "," + (locked.get() ? "T" : "F") + "]";
    }

    public int compareTo(Guard g) {
        return this.id - g.id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Guard guard = (Guard) o;
        return id == guard.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    final AtomicReference<GuardTask> next = new AtomicReference<>();

    public CompletableFuture<Void> runGuarded(Runnable r) {
        var done = new CompletableFuture<Void>();
        
        runGuarded_(GuardSet.of(this), () -> {
            r.run();
            done.complete(null);
        });
        
        return done;
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
    public static CompletableFuture<Void> runGuarded(final Guard g1, Runnable r) {
        return g1.runGuarded(r);
    }

    // Multiple guards
    public static CompletableFuture<Void> runGuarded(Runnable r, final Guard... guards) {
        if (guards.length == 0) {
            r.run();
            return CompletableFuture.completedFuture(null);
        } else if (guards.length == 1) {
            return guards[0].runGuarded(r);
        } else {
            return runGuarded(GuardSet.of(guards), r);
        }
    }

    public static CompletableFuture<Void> runGuarded(GuardSet gs, Runnable r) {
        if (gs.size() == 1) {
            return runGuarded(gs.get(0), r);
        }

        var done = new CompletableFuture<Void>();
        assert gs.size() > 1;

        List<GuardTask> guardTasks = new ArrayList<>();
        for (Guard guard : gs) {
            guardTasks.add(new GuardTask(guard, gs));
        }

        int last = gs.size() - 1;
        assert guardTasks.size() == gs.size();

        // Set up the penultimate task
        guardTasks.get(last - 1).setRun(() -> {
            // Set up the ultimate task
            gs.get(last).runGuarded_(gs, () -> {
                r.run();
                done.complete(null);
                
                // Last to run unlocks everything
                for (int i = 0; i < last; i++) {
                    guardTasks.get(i).free(i < last - 1);
                }
            });
        });

        // Prior tasks, each calls the next
        for (int i = 0; i < last - 1; i++) {
            final int next = i + 1;
            final var guardTask = guardTasks.get(i);
            final var guardNext = gs.get(next);
            final var guardTaskNext = guardTasks.get(next);
            guardTask.setRun(() -> {
                guardNext.dummyRunGuarded(guardTaskNext);
            });
        }
        
        // Kick the whole thing off
        gs.get(0).dummyRunGuarded(guardTasks.get(0));
        
        return done;
    }

    public static CondContext<CondTask0> newCondition(GuardSet guards) {
        return CondContext.newCond(guards);
    }

    public static CondContext<CondTask0> newCondition(Guard... guards) {
        return CondContext.newCond(guards);
    }

    public static CompletableFuture<Void> runCondition(final CondContext<CondTask0> ctx, final CondCheck0 c) {
        return Guard.runCondition(ctx, new CondTask0(c));
    }

    public static <T> CompletableFuture<Void> runCondition(final CondContext<CondTask0> ctx,
                                                           final CondTask0 c) {
        return ctx.runCondition(c);
    }

    public static <T> CondContext<CondTask1<T>> newCondition(final GuardVar<T> gv1) {
        return CondContext.newCond(gv1);
    }

    public static <T> CompletableFuture<Void> runCondition(final CondContext<CondTask1<T>> ctx,
                                                           final CondCheck1<T> c) {
        return Guard.runCondition(ctx, new CondTask1<>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<Void> runCondition(final CondContext<CondTask1<T>> ctx,
                                                           final CondTask1<T> c) {
        c.set1((Var<T>) ctx.getVar(0));
        return ctx.runCondition(c);
    }

    public static <T> CompletableFuture<Void> runGuarded(final GuardVar<T> g, final GuardTask1<T> c) {
        return g.runGuarded(() -> c.run(g.var));
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

// region Generated by mkguards.py

    public static <T1, T2> CondContext<CondTask2<T1, T2>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2) {
        return CondContext.newCond(gv1, gv2);
    }

    public static <T1, T2> CompletableFuture<Void> runCondition(
            final CondContext<CondTask2<T1, T2>> ctx,
            final CondCheck2<T1, T2> c) {
        return Guard.runCondition(ctx, new CondTask2<T1, T2>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2> CompletableFuture<Void> runCondition(
            final CondContext<CondTask2<T1, T2>> ctx,
            final CondTask2<T1, T2> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        return ctx.runCondition(c);
    }

    public static <T1, T2> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2), () -> c.run(gv1.var, gv2.var));
    }

    public static <T1, T2> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardTask2<T1, T2> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2)), () -> c.run(gv1.var, gv2.var));
    }

    public static <T1, T2, T3> CondContext<CondTask3<T1, T2, T3>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3) {
        return CondContext.newCond(gv1, gv2, gv3);
    }

    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final CondContext<CondTask3<T1, T2, T3>> ctx,
            final CondCheck3<T1, T2, T3> c) {
        return Guard.runCondition(ctx, new CondTask3<T1, T2, T3>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3> CompletableFuture<Void> runCondition(
            final CondContext<CondTask3<T1, T2, T3>> ctx,
            final CondTask3<T1, T2, T3> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3), () -> c.run(gv1.var, gv2.var, gv3.var));
    }

    public static <T1, T2, T3> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardTask3<T1, T2, T3> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3)), () -> c.run(gv1.var, gv2.var, gv3.var));
    }


    public static <T1, T2, T3, T4> CondContext<CondTask4<T1, T2, T3, T4>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4) {
        return CondContext.newCond(gv1, gv2, gv3, gv4);
    }

    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final CondContext<CondTask4<T1, T2, T3, T4>> ctx,
            final CondCheck4<T1, T2, T3, T4> c) {
        return Guard.runCondition(ctx, new CondTask4<T1, T2, T3, T4>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4> CompletableFuture<Void> runCondition(
            final CondContext<CondTask4<T1, T2, T3, T4>> ctx,
            final CondTask4<T1, T2, T3, T4> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }

    public static <T1, T2, T3, T4> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardTask4<T1, T2, T3, T4> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var));
    }


    public static <T1, T2, T3, T4, T5> CondContext<CondTask5<T1, T2, T3, T4, T5>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5);
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final CondContext<CondTask5<T1, T2, T3, T4, T5>> ctx,
            final CondCheck5<T1, T2, T3, T4, T5> c) {
        return Guard.runCondition(ctx, new CondTask5<T1, T2, T3, T4, T5>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runCondition(
            final CondContext<CondTask5<T1, T2, T3, T4, T5>> ctx,
            final CondTask5<T1, T2, T3, T4, T5> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var));
    }


    public static <T1, T2, T3, T4, T5, T6> CondContext<CondTask6<T1, T2, T3, T4, T5, T6>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6);
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final CondContext<CondTask6<T1, T2, T3, T4, T5, T6>> ctx,
            final CondCheck6<T1, T2, T3, T4, T5, T6> c) {
        return Guard.runCondition(ctx, new CondTask6<T1, T2, T3, T4, T5, T6>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runCondition(
            final CondContext<CondTask6<T1, T2, T3, T4, T5, T6>> ctx,
            final CondTask6<T1, T2, T3, T4, T5, T6> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7> CondContext<CondTask7<T1, T2, T3, T4, T5, T6, T7>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runCondition(
            final CondContext<CondTask7<T1, T2, T3, T4, T5, T6, T7>> ctx,
            final CondCheck7<T1, T2, T3, T4, T5, T6, T7> c) {
        return Guard.runCondition(ctx, new CondTask7<T1, T2, T3, T4, T5, T6, T7>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runCondition(
            final CondContext<CondTask7<T1, T2, T3, T4, T5, T6, T7>> ctx,
            final CondTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Void> runGuardedEtAl(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        var held = GuardTask.GUARDS_HELD.get();
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8> CondContext<CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runCondition(
            final CondContext<CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>> ctx,
            final CondCheck8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        return Guard.runCondition(ctx, new CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runCondition(
            final CondContext<CondTask8<T1, T2, T3, T4, T5, T6, T7, T8>> ctx,
            final CondTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runGuarded(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CondContext<CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runCondition(
            final CondContext<CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> ctx,
            final CondCheck9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        return Guard.runCondition(ctx, new CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runCondition(
            final CondContext<CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> ctx,
            final CondTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CondContext<CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> newCondition(
            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runCondition(
            final CondContext<CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> ctx,
            final CondCheck10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        return Guard.runCondition(ctx, new CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runCondition(
            final CondContext<CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> ctx,
            final CondTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CondContext<CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> newCondition(
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
            final GuardVar<T11> gv11) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runCondition(
            final CondContext<CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> ctx,
            final CondCheck11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        return Guard.runCondition(ctx, new CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runCondition(
            final CondContext<CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> ctx,
            final CondTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CondContext<CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> newCondition(
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
            final GuardVar<T12> gv12) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runCondition(
            final CondContext<CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> ctx,
            final CondCheck12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        return Guard.runCondition(ctx, new CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runCondition(
            final CondContext<CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> ctx,
            final CondTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CondContext<CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> newCondition(
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
            final GuardVar<T13> gv13) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runCondition(
            final CondContext<CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> ctx,
            final CondCheck13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        return Guard.runCondition(ctx, new CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runCondition(
            final CondContext<CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> ctx,
            final CondTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CondContext<CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> newCondition(
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
            final GuardVar<T14> gv14) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runCondition(
            final CondContext<CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> ctx,
            final CondCheck14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        return Guard.runCondition(ctx, new CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runCondition(
            final CondContext<CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> ctx,
            final CondTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CondContext<CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> newCondition(
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
            final GuardVar<T15> gv15) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runCondition(
            final CondContext<CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> ctx,
            final CondCheck15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        return Guard.runCondition(ctx, new CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runCondition(
            final CondContext<CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> ctx,
            final CondTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        c.set15((Var<T15>) ctx.getVar(14));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CondContext<CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> newCondition(
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
            final GuardVar<T16> gv16) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runCondition(
            final CondContext<CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> ctx,
            final CondCheck16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        return Guard.runCondition(ctx, new CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runCondition(
            final CondContext<CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> ctx,
            final CondTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        c.set15((Var<T15>) ctx.getVar(14));
        c.set16((Var<T16>) ctx.getVar(15));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CondContext<CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> newCondition(
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
            final GuardVar<T17> gv17) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runCondition(
            final CondContext<CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> ctx,
            final CondCheck17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        return Guard.runCondition(ctx, new CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runCondition(
            final CondContext<CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> ctx,
            final CondTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        c.set15((Var<T15>) ctx.getVar(14));
        c.set16((Var<T16>) ctx.getVar(15));
        c.set17((Var<T17>) ctx.getVar(16));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CondContext<CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> newCondition(
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
            final GuardVar<T18> gv18) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runCondition(
            final CondContext<CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> ctx,
            final CondCheck18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        return Guard.runCondition(ctx, new CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runCondition(
            final CondContext<CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> ctx,
            final CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        c.set15((Var<T15>) ctx.getVar(14));
        c.set16((Var<T16>) ctx.getVar(15));
        c.set17((Var<T17>) ctx.getVar(16));
        c.set18((Var<T18>) ctx.getVar(17));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var));
    }


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CondContext<CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> newCondition(
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
            final GuardVar<T19> gv19) {
        return CondContext.newCond(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runCondition(
            final CondContext<CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> ctx,
            final CondCheck19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        return Guard.runCondition(ctx, new CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>(c));
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runCondition(
            final CondContext<CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> ctx,
            final CondTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        c.set1((Var<T1>) ctx.getVar(0));
        c.set2((Var<T2>) ctx.getVar(1));
        c.set3((Var<T3>) ctx.getVar(2));
        c.set4((Var<T4>) ctx.getVar(3));
        c.set5((Var<T5>) ctx.getVar(4));
        c.set6((Var<T6>) ctx.getVar(5));
        c.set7((Var<T7>) ctx.getVar(6));
        c.set8((Var<T8>) ctx.getVar(7));
        c.set9((Var<T9>) ctx.getVar(8));
        c.set10((Var<T10>) ctx.getVar(9));
        c.set11((Var<T11>) ctx.getVar(10));
        c.set12((Var<T12>) ctx.getVar(11));
        c.set13((Var<T13>) ctx.getVar(12));
        c.set14((Var<T14>) ctx.getVar(13));
        c.set15((Var<T15>) ctx.getVar(14));
        c.set16((Var<T16>) ctx.getVar(15));
        c.set17((Var<T17>) ctx.getVar(16));
        c.set18((Var<T18>) ctx.getVar(17));
        c.set19((Var<T19>) ctx.getVar(18));
        return ctx.runCondition(c);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runGuarded(
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
        return Guard.runGuarded(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> CompletableFuture<Void> runGuardedEtAl(
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
        return Guard.runGuarded(held.union(GuardSet.of(gv1, gv2, gv3, gv4, gv5, gv6, gv7, gv8, gv9, gv10, gv11, gv12, gv13, gv14, gv15, gv16, gv17, gv18, gv19)), () -> c.run(gv1.var, gv2.var, gv3.var, gv4.var, gv5.var, gv6.var, gv7.var, gv8.var, gv9.var, gv10.var, gv11.var, gv12.var, gv13.var, gv14.var, gv15.var, gv16.var, gv17.var, gv18.var, gv19.var));
    }

// endregion

}
