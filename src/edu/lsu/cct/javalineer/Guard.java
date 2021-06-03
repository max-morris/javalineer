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
    static AtomicInteger idSeq = new AtomicInteger(0);
    public final int id = idSeq.getAndIncrement();

    public int compareTo(Guard g) {
        return id - g.id;
    }
    
    public String toString() {
        return "Guard("+id+")";
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

    public static void runGuarded(Guard g, Runnable r) {
        g.runGuarded(r);
    }
    public static  <T> void runGuarded(final GuardVar<T> g, final GuardTask1<T> c) {
        g.runGuarded(()->{ c.run(g.var); });
    }
    public static <T1,T2> void runGuarded(final GuardVar<T1> g1,final GuardVar<T2> g2, final GuardTask2<T1,T2> c) {
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);
        Guard.runGuarded(ts,()->{ c.run(g1.var,g2.var); });
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

    public static <T> void runCondition(
            GuardVar<T> gv,
            final CondCheck1<T> c) {
        Guard.runCondition(gv,new CondTask1<T>(c));
    }

    public static <T> void runCondition(
            GuardVar<T> gv,
            final CondTask1<T> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv);
        c.set1(gv.var);
        runCondition(ts,c);
    }

    public static <T1,T2> void runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            final CondCheck2<T1,T2> c) {
        Guard.runCondition(gv1,gv2,new CondTask2<T1,T2>(c));
    }

    public static <T1,T2> void runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            final CondTask2<T1,T2> c) {
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(gv1);
        ts.add(gv2);
        c.set1(gv1.var);
        c.set2(gv2.var);
        runCondition(ts,c);
    }

    public static <T1,T2,T3> void runCondition(
            GuardVar<T1> gv1,
            GuardVar<T2> gv2,
            GuardVar<T3> gv3,
            final CondCheck3<T1,T2,T3> c) {
        Guard.runCondition(gv1,gv2,gv3,new CondTask3<T1,T2,T3>(c));
    }

    public static <T1,T2,T3> void runCondition(
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
        runCondition(ts,c);
    }

    public static void runCondition(final TreeSet<Guard> ts, final CondAct ca) {
        runCondition(ts, new CondTask() {
            public void run() {
                if (done) {
                    return;
                }

                final CompletableFuture<Boolean> result = new CompletableFuture<>();
                ca.act(result);

                result.whenComplete((res, err) -> {
                    if (err != null) {
                        // TODO: Not sure that this is the proper thing to do for exceptions
                        err.printStackTrace();
                        done = true;
                    } else {
                        assert res != null;
                        done = res;
                    }
                });
            }
        });
    }

    public static void runCondition(final TreeSet<Guard> ts,final CondTask c) {
        assert ts.size() > 0;
        Cond cond = new Cond();
        cond.task = c;
        cond.gset = ts;
        for(Guard g : ts)
            g.cmgr.add(new CondLink(cond));
        Guard.runGuarded(ts,c);
    }
}
