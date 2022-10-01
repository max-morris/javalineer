/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author sbrandt
 */
public class Guard implements Comparable<Guard> {

    public String getName() {
        if(getClass().desiredAssertionStatus()) {
            Throwable t = new Throwable();
            StackTraceElement[] se = t.getStackTrace();
            int n = 2;
            String name = ":Guard["+se[n]+"]";
            while(name.indexOf("edu.lsu.cct.javalineer") >= 0)
                name = ":Guard["+se[++n]+"]";
            return name;
        } else {
            return "";
        }
    }

    final String name = getName();
    static AtomicInteger idSeq = new AtomicInteger(0);
    public final int id = idSeq.getAndIncrement();

    public int compareTo(Guard g) {
        return id - g.id;
    }
    
    public String toString() {
        return "Guard("+id+":"+name+")";
    }

    public Guard getGuard() {
        return this;
    }

    AtomicReference<GuardTask> task = new AtomicReference<>(null);

    public static boolean has(Guard g) {
        TreeSet<Guard> ts = GuardTask.GUARDS_HELD.get();
        if(ts == null) return false;
        return ts.contains(g);
    }

    public static boolean has(TreeSet<Guard> guards) {
        TreeSet<Guard> ts = GuardTask.GUARDS_HELD.get();
        if (ts == null) {
            return false;
        }
        return ts.containsAll(guards);
    }

    public void runGuarded(Runnable r) {
        TreeSet<Guard> tg = new TreeSet<>();
        tg.add(this);
        runGuarded(tg, r);
    }

    public void nowOrNever(Runnable r) {
        TreeSet<Guard> tg = new TreeSet<>();
        tg.add(this);
        nowOrNever(tg, r);
    }

    public void nowOrElse(Runnable r, Runnable orElse) {
        TreeSet<Guard> tg = new TreeSet<>();
        tg.add(this);
        nowOrElse(tg, r, orElse);
    }

    public static void runGuarded(Guard g, Runnable r) {
        g.runGuarded(r);
    }

    public static <T> void runGuarded(final GuardVar<T> g, final GuardTask1<T> c) {
        g.runGuarded(()->{ c.run(g.var); });
    }

    public static <T1> void runGuardedEtAl(final GuardVar<T1> g1, final GuardTask1<T1> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        Guard.runGuarded(ts, () -> c.run(g1.var));
    }

    public static <T1,T2> void runGuarded(final GuardVar<T1> g1, final GuardVar<T2> g2, final GuardTask2<T1,T2> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var));
    }

    public static <T1, T2> void runGuardedEtAl(final GuardVar<T1> g1, final GuardVar<T2> g2, final GuardTask2<T1, T2> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        ts.add(g2);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var));
    }

    public static <T1,T2,T3> void runGuarded(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardTask3<T1,T2,T3> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        Guard.runGuarded(ts,()->{ c.run(g1.var,g2.var,g3.var); });
    }

    public static <T1, T2, T3> void runGuardedEtAl(final GuardVar<T1> g1, final GuardVar<T2> g2, final GuardVar<T3> g3, final GuardTask3<T1, T2, T3> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var));
    }

    public static <T1, T2, T3, T4> void runGuarded(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardTask4<T1, T2, T3, T4> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var));
    }

    public static <T1, T2, T3, T4> void runGuardedEtAl(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardTask4<T1, T2, T3, T4> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var));
    }

    public static <T1, T2, T3, T4, T5> void runGuarded(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardVar<T5> g5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        ts.add(g5);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var, g5.var));
    }

    public static <T1, T2, T3, T4, T5> void runGuardedEtAl(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardVar<T5> g5,
            final GuardTask5<T1, T2, T3, T4, T5> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        ts.add(g5);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var, g5.var));
    }

    public static <T1, T2, T3, T4, T5, T6> void runGuarded(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardVar<T5> g5,
            final GuardVar<T6> g6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        ts.add(g5);
        ts.add(g6);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var, g5.var, g6.var));
    }

    public static <T1, T2, T3, T4, T5, T6> void runGuardedEtAl(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardVar<T4> g4,
            final GuardVar<T5> g5,
            final GuardVar<T6> g6,
            final GuardTask6<T1, T2, T3, T4, T5, T6> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());

        ts.add(g1);
        ts.add(g2);
        ts.add(g3);
        ts.add(g4);
        ts.add(g5);
        ts.add(g6);
        Guard.runGuarded(ts, () -> c.run(g1.var, g2.var, g3.var, g4.var, g5.var, g6.var));
    }

    public static <T> void nowOrNever(final GuardVar<T> g, final GuardTask1<T> c) {
        g.nowOrNever(() -> c.run(g.var));
    }

    public static <T1,T2> void nowOrNever(final GuardVar<T1> g1, final GuardVar<T2> g2, final GuardTask2<T1,T2> c) {
        Guard.nowOrNever(new TreeSet<>() {{ add(g1); add(g2); }}, () -> c.run(g1.var, g2.var));
    }

    public static <T1,T2,T3> void nowOrNever(
            final GuardVar<T1> g1,
            final GuardVar<T2> g2,
            final GuardVar<T3> g3,
            final GuardTask3<T1,T2,T3> c) {
        Guard.nowOrNever(new TreeSet<>() {{ add(g1); add(g2); add(g3); }}, () -> c.run(g1.var, g2.var, g3.var));
    }

    public static <T> void nowOrElse(final GuardVar<T> g, final GuardTask1<T> c, final Runnable orElse) {
        g.nowOrElse(() -> c.run(g.var), orElse);
    }

    public static <T1,T2> void nowOrElse(final GuardVar<T1> g1, final GuardVar<T2> g2, final GuardTask2<T1,T2> c, final Runnable orElse) {
        Guard.nowOrElse(new TreeSet<>() {{ add(g1); add(g2); }}, () -> c.run(g1.var, g2.var), orElse);
    }

    public static <T1,T2,T3> void nowOrElse(final GuardVar<T1> g1,
                                            final GuardVar<T2> g2,
                                            final GuardVar<T3> g3,
                                            final GuardTask3<T1,T2,T3> c,
                                            final Runnable orElse) {
        Guard.nowOrElse(new TreeSet<>() {{ add(g1); add(g2); add(g3); }}, () -> c.run(g1.var, g2.var, g3.var), orElse);
    }

    private static <T> CompletableFuture<Void> setNow(final GuardVar<T> gv, final AtomicReference<Optional<Var<T>>> ref) {
        final var fut = new CompletableFuture<Void>();
        gv.nowOrElse(() -> {
            ref.set(Optional.of(gv.var));
            fut.complete(null);
        }, () -> {
            ref.set(Optional.empty());
            fut.complete(null);
        });
        return fut;
    }

    public static <T> void now(final GuardVar<T> g, final OptionalGuardTask1<T> c) {
        g.nowOrElse(() -> c.run(Optional.of(g.var)), () -> c.run(Optional.empty()));
    }

    public static <T1, T2> void now(final GuardVar<T1> g1, final GuardVar<T2> g2, final OptionalGuardTask2<T1, T2> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();

        CompletableFuture.allOf(setNow(g1, o1), setNow(g2, o2))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(g1); add(g2); }},
                                  () -> c.run(o1.get(), o2.get())));
    }

    public static <T1, T2, T3> void now(final GuardVar<T1> g1,
                                        final GuardVar<T2> g2,
                                        final GuardVar<T3> g3,
                                        final OptionalGuardTask3<T1, T2, T3> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();

        CompletableFuture.allOf(setNow(g1, o1), setNow(g2, o2), setNow(g3, o3))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(g1); add(g2); add(g3); }},
                                  () -> c.run(o1.get(), o2.get(), o3.get())));
    }

    public static <T1, T2, T3, T4> void now(final GuardVar<T1> g1,
                                            final GuardVar<T2> g2,
                                            final GuardVar<T3> g3,
                                            final GuardVar<T4> g4,
                                            final OptionalGuardTask4<T1, T2, T3, T4> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();

        CompletableFuture.allOf(setNow(g1, o1), setNow(g2, o2), setNow(g3, o3), setNow(g4, o4))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(g1); add(g2); add(g3); add(g4); }},
                                  () -> c.run(o1.get(), o2.get(), o3.get(), o4.get())));
    }

    public static void runGuarded(TreeSet<Guard> gset, Runnable r) {
        GuardTask gt = new GuardTask(gset,r);
        gt.run();
    }

    public static void nowOrNever(Guard g, Runnable r) {
        nowOrNever(new TreeSet<>() {{add(g);}}, r);
    }

    public static void nowOrNever(TreeSet<Guard> gSet, Runnable r) {
        GuardTask gt = new GuardTask(gSet, () -> {
            if (Guard.has(gSet)) {
                r.run();
            }
        });
        gt.runImmediately();
    }

    /*
     * This is private because we only want it called from inside Guard#now.
     */
    private static void runAlways(TreeSet<Guard> gSet, Runnable r) {
        GuardTask gt = new GuardTask(gSet, r);
        gt.runImmediately();
    }

    public static void nowOrElse(TreeSet<Guard> gSet, Runnable r, Runnable orElse) {
        GuardTask gt = new GuardTask(gSet, () -> {
            if (Guard.has(gSet)) {
                r.run();
            } else {
                orElse.run();
            }
        });
        gt.runImmediately();
    }

    public static void nowOrElse(Guard g, Runnable r, Runnable orElse) {
        nowOrElse(new TreeSet<>() {{ add(g); }}, r, orElse);
    }

    CondMgr cmgr = new CondMgr();

    public void signal() {
        cmgr.signal();
    }

    public void signalAll() {
        cmgr.signalAll();
    }

    public static <T> CompletableFuture<Void> runCondition(
            GuardVar<T> gv,
            final CondCheck1<T> c) {
        return Guard.runCondition(gv,new CondTask1<T>(c));
    }

    public static <T> CompletableFuture<Void> runCondition(
            GuardVar<T> gv,
            final CondTask1<T> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv);
        c.set1(gv.var);
        return runCondition(ts,c);
    }

    public static <T1,T2> CompletableFuture<Void> runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            final CondCheck2<T1,T2> c) {
        return Guard.runCondition(gv1,gv2,new CondTask2<T1,T2>(c));
    }

    public static <T1,T2> CompletableFuture<Void> runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            final CondTask2<T1,T2> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        c.set1(gv1.var);
        c.set2(gv2.var);
        return runCondition(ts,c);
    }

    public static <T1,T2,T3> CompletableFuture<Void> runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            GuardVar<T3> gv3,
            final CondCheck3<T1,T2,T3> c) {
        return Guard.runCondition(gv1,gv2,gv3,new CondTask3<T1,T2,T3>(c));
    }

    public static <T1,T2,T3> CompletableFuture<Void> runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            GuardVar<T3> gv3,
            final CondTask3<T1,T2,T3> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        return runCondition(ts,c);
    }

    public static CompletableFuture<Void> runCondition(final TreeSet<Guard> ts,final CondTask c) {
        assert ts.size() > 0;
        Cond cond = new Cond();
        cond.task = c;
        cond.gset = ts;
        for(Guard g : ts)
            g.cmgr.add(new CondLink(cond));
        Guard.runGuarded(ts,c);
        return cond.task.fut;
    }
}
