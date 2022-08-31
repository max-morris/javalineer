/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author sbrandt
 */
public class GuardTask {
    static AtomicInteger idSeq = new AtomicInteger(0);
    final int id = idSeq.getAndIncrement();
    
    public String toString() {
        return "GuardTask("+id+")";
    }
    
    public final static GuardTask DONE = new GuardTask(new TreeSet<>(),()->{});
    final List<AtomicReference<GuardTask>> next = new ArrayList<>();

    private final List<Guard> gset = new ArrayList<>();
    private final Runnable r;
    private volatile int index = 0;

    public final static ThreadLocal<TreeSet<Guard>> GUARDS_HELD = new ThreadLocal<>();

    private final AtomicInteger timesRun = new AtomicInteger(0);

    static void run(Runnable r,List<Guard> gset) {
        try {
            TreeSet<Guard> ts = new TreeSet<>();
            ts.addAll(gset);
            assert GUARDS_HELD.get() == null;
            GUARDS_HELD.set(ts);
            r.run();
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        } catch(AssertionError e) {
            e.printStackTrace();
            System.exit(2);
        } finally {
            GUARDS_HELD.set(null);
        }
    }

    GuardTask(TreeSet<Guard> gset, Runnable r) {
        this.gset.addAll(gset);
        for(Guard g : gset) {
            next.add(new AtomicReference<>());
        }
        this.r = r;
    }

    public void run() {
        if(gset.size()==0) {
            //assert flagRun() : String.format("Task [%d] is running more than once!", id);
            flagRunThrow();
            run(r,gset);
            return;
        }
        Guard g = gset.get(index);
        GuardTask prev = g.task.getAndSet(this);
        if(prev == null) {
            //assert flagRun() : String.format("Task [%d] is running more than once!", id);
            flagRunThrow();
            runTask(g);
        } else {
            assert prev.next.size() == prev.gset.size();
            var nextt = prev.next.get(prev.index);
            assert nextt != null;
            if(!nextt.compareAndSet(null,this)) {
                //assert flagRun() : String.format("Task [%d] is running more than once!", id);
                flagRunThrow();
                runTask(g);
            }
            assert nextt.get() != null;
        }
    }

    public void runImmediately() {
        //assert flagRun() : String.format("Task [%d] is running more than once!", id);
        flagRunThrow();

        if(gset.size() == 0) {
            run(r, gset);
            return;
        }
        Guard g = gset.get(index);
        GuardTask prev = g.task.get();
        if(prev == null) {
            if (g.task.compareAndSet(null, this)) {
                runTask(g);
            } else {
                runTask(null);
            }
        } else {
            assert prev.next.size() == prev.gset.size();
            var nextt = prev.next.get(prev.index);
            assert nextt != null;
            if (nextt.get() == null) {
                runTask(null);
            } else if (g.task.compareAndSet(prev, this)) {
                runTask(g);
            } else {
                runTask(null);
            }
        }
    }
    
    private void free() {
        Guard g = gset.get(index);
        if (!next.get(index).compareAndSet(null, DONE)) {
            GuardTask prev = next.get(index).get();
            assert prev != DONE;
            //assert prev.flagRun() : String.format("Task [%d] is running more than once!", prev.id);
            prev.flagRunThrow();
            prev.runTask(g);
        }
        assert next.get(index).get() != null;
        if (index > 0) {
            index--;
            free();
        }
    }

    private void runTask(Guard g) {
        if(index + 1 == gset.size()) {
            // Don't need to force async
            Pool.run(()->{
                if (g == null) {
                    run(r, List.of());
                } else {
                    run(r, gset);
                }
                free();
            });
        } else {
            index++;
            assert index < gset.size();
            run();
        }
    }

    private boolean flagRun() {
        return timesRun.incrementAndGet() == 1;
    }

    private void flagRunThrow() {
        if (timesRun.incrementAndGet() != 1) {
            var bad = String.format("Task [%d] is running more than once!", this.id);
            System.out.println(bad);
            System.out.flush();
            throw new Error(bad);
        }
    }
}
