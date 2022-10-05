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

    // -> begin generated

    public static <T1, T2> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
 final OptionalGuardTask2<T1, T2> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2); }},
                                  () -> c.run( o1.get(), o2.get() )));
    }
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
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        c.set1(gv1.var);
        c.set2(gv2.var);
        return runCondition(ts,c);
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


    public static <T1, T2, T3> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
 final OptionalGuardTask3<T1, T2, T3> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get() )));
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
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
 final OptionalGuardTask4<T1, T2, T3, T4> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get() )));
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
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        ts.add(gv3);
        ts.add(gv4);
        c.set1(gv1.var);
        c.set2(gv2.var);
        c.set3(gv3.var);
        c.set4(gv4.var);
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
 final OptionalGuardTask5<T1, T2, T3, T4, T5> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
 final OptionalGuardTask6<T1, T2, T3, T4, T5, T6> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
 final OptionalGuardTask7<T1, T2, T3, T4, T5, T6, T7> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
 final OptionalGuardTask8<T1, T2, T3, T4, T5, T6, T7, T8> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
 final OptionalGuardTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> void now(            final GuardVar<T1> gv1,
            final GuardVar<T2> gv2,
            final GuardVar<T3> gv3,
            final GuardVar<T4> gv4,
            final GuardVar<T5> gv5,
            final GuardVar<T6> gv6,
            final GuardVar<T7> gv7,
            final GuardVar<T8> gv8,
            final GuardVar<T9> gv9,
            final GuardVar<T10> gv10,
 final OptionalGuardTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();
        final var o15 = new AtomicReference<Optional<Var<T15>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14), setNow(gv15,o15))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14);add(gv15); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get(), o15.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();
        final var o15 = new AtomicReference<Optional<Var<T15>>>();
        final var o16 = new AtomicReference<Optional<Var<T16>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14), setNow(gv15,o15), setNow(gv16,o16))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14);add(gv15);add(gv16); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get(), o15.get(), o16.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();
        final var o15 = new AtomicReference<Optional<Var<T15>>>();
        final var o16 = new AtomicReference<Optional<Var<T16>>>();
        final var o17 = new AtomicReference<Optional<Var<T17>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14), setNow(gv15,o15), setNow(gv16,o16), setNow(gv17,o17))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14);add(gv15);add(gv16);add(gv17); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get(), o15.get(), o16.get(), o17.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();
        final var o15 = new AtomicReference<Optional<Var<T15>>>();
        final var o16 = new AtomicReference<Optional<Var<T16>>>();
        final var o17 = new AtomicReference<Optional<Var<T17>>>();
        final var o18 = new AtomicReference<Optional<Var<T18>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14), setNow(gv15,o15), setNow(gv16,o16), setNow(gv17,o17), setNow(gv18,o18))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14);add(gv15);add(gv16);add(gv17);add(gv18); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get(), o15.get(), o16.get(), o17.get(), o18.get() )));
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
        return runCondition(ts,c);
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


    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> void now(            final GuardVar<T1> gv1,
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
 final OptionalGuardTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> c) {
        final var o1 = new AtomicReference<Optional<Var<T1>>>();
        final var o2 = new AtomicReference<Optional<Var<T2>>>();
        final var o3 = new AtomicReference<Optional<Var<T3>>>();
        final var o4 = new AtomicReference<Optional<Var<T4>>>();
        final var o5 = new AtomicReference<Optional<Var<T5>>>();
        final var o6 = new AtomicReference<Optional<Var<T6>>>();
        final var o7 = new AtomicReference<Optional<Var<T7>>>();
        final var o8 = new AtomicReference<Optional<Var<T8>>>();
        final var o9 = new AtomicReference<Optional<Var<T9>>>();
        final var o10 = new AtomicReference<Optional<Var<T10>>>();
        final var o11 = new AtomicReference<Optional<Var<T11>>>();
        final var o12 = new AtomicReference<Optional<Var<T12>>>();
        final var o13 = new AtomicReference<Optional<Var<T13>>>();
        final var o14 = new AtomicReference<Optional<Var<T14>>>();
        final var o15 = new AtomicReference<Optional<Var<T15>>>();
        final var o16 = new AtomicReference<Optional<Var<T16>>>();
        final var o17 = new AtomicReference<Optional<Var<T17>>>();
        final var o18 = new AtomicReference<Optional<Var<T18>>>();
        final var o19 = new AtomicReference<Optional<Var<T19>>>();

        CompletableFuture.allOf(setNow(gv1,o1), setNow(gv2,o2), setNow(gv3,o3), setNow(gv4,o4), setNow(gv5,o5), setNow(gv6,o6), setNow(gv7,o7), setNow(gv8,o8), setNow(gv9,o9), setNow(gv10,o10), setNow(gv11,o11), setNow(gv12,o12), setNow(gv13,o13), setNow(gv14,o14), setNow(gv15,o15), setNow(gv16,o16), setNow(gv17,o17), setNow(gv18,o18), setNow(gv19,o19))
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{ add(gv1);add(gv2);add(gv3);add(gv4);add(gv5);add(gv6);add(gv7);add(gv8);add(gv9);add(gv10);add(gv11);add(gv12);add(gv13);add(gv14);add(gv15);add(gv16);add(gv17);add(gv18);add(gv19); }},
                                  () -> c.run( o1.get(), o2.get(), o3.get(), o4.get(), o5.get(), o6.get(), o7.get(), o8.get(), o9.get(), o10.get(), o11.get(), o12.get(), o13.get(), o14.get(), o15.get(), o16.get(), o17.get(), o18.get(), o19.get() )));
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
        return runCondition(ts,c);
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

    // -> end generated

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
