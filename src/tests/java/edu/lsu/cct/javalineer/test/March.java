// TODO: Make this work with new condition variables

/*
package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck2;

import java.util.*;
import java.util.concurrent.CompletableFuture;

class ProdCon {

    static int idSeq = 0;
    final int id = idSeq++;

    private volatile boolean readyToProduce = false;
    private volatile boolean readyToConsume = true;
    private volatile boolean alwaysReady = false;
    private volatile int val = 0;

    public ProdCon(boolean a) {
        alwaysReady = a;
    }

    boolean isReadyToProduce(int n) {
        if (alwaysReady)
            return true;
        if (val != n) {
            //System.out.printf("not ready to produce: %d != %d: %d%n",val,n,id);
            return false;
        }
        return alwaysReady || readyToProduce;
    }

    boolean isReadyToConsume(int n) {
        if (alwaysReady)
            return true;
        if (val != n) {
            //System.out.printf("not ready to consume: %d != %d: %d%n",val,n,id);
            return false;
        }
        return alwaysReady || readyToConsume;
    }

    void produce(int n) {
        if (alwaysReady)
            return;
        //System.out.println("produce: "+n+","+id);
        readyToProduce = false;
        readyToConsume = true;
        assert n >= val : "" + n + " >= " + val;
        val = n;
    }

    void consume(int n) {
        if (alwaysReady)
            return;
        //System.out.println("consume: "+n+","+id);
        readyToProduce = true;
        readyToConsume = false;
    }
}

class Segment {
    final static boolean ALL_ = true;
    final static int N_STEPS = 100;
    final static int N_SEGS = 100;

    final int id;

    final CompletableFuture<Void> done = new CompletableFuture<>();

    GuardVar<ProdCon> left = new GuardVar<ProdCon>(new ProdCon(false));
    GuardVar<ProdCon> right = new GuardVar<ProdCon>(new ProdCon(false));
    GuardVar<ProdCon> neighborLeft, neighborRight;

    public String toString() {
        return "" + id + "{" +
                left.toString() + "," +
                right.toString() + "," +
                neighborLeft.toString() + "," +
                neighborRight.toString() +
                "}";
    }

    Segment(int id) {
        this.id = id;
    }

    volatile static boolean complete = false;

    void runStep(final int step) {
        if (step == N_STEPS) {
            if (id == 0) {
                System.out.println("Complete!");
            }
            complete = true;
            done.complete(null);
            return;
        }
        if (id == 0)
            System.out.println("Running step: " + step + " id=" + id);
        //condition with(left => l, right => r)
        Guard.runCondition(left, right, new CondCheck2<>() {
            public boolean check(Var<ProdCon> l_, Var<ProdCon> r_) {
                ProdCon l = l_.get();
                ProdCon r = r_.get();
                //assert this guardedby left.getGuard();
                //assert this guardedby right.getGuard();
                if (!l.isReadyToConsume(step)) {
                    return false;
                }
                if (!r.isReadyToConsume(step)) {
                    return false;
                }
                //System.out.printf("Stage1: step=%d, id=%d%n",step,id);
                l.consume(step);
                r.consume(step);
                assert r.id != l.id;
                if (ALL_) {
                    left.signalAll();
                    right.signalAll();
                } else {
                    left.signal();
                    right.signal();
                }
                //ignore { Thread.sleep(100); }
                //condition with(neighborLeft => nl, neighborRight => nr)
                Guard.runCondition(neighborLeft, neighborRight, new CondCheck2<>() {
                    @Override
                    public boolean check(Var<ProdCon> nl_, Var<ProdCon> nr_) {
                        ProdCon nl = nl_.get();
                        ProdCon nr = nr_.get();
                        //assert this guardedby neighborLeft.getGuard();
                        //assert this guardedby neighborRight.getGuard();
                        if (!nl.isReadyToProduce(step)) {
                            return false;
                        }
                        if (!nr.isReadyToProduce(step)) {
                            return false;
                        }
                        //System.out.printf("Stage2: step=%d, id=%d%n",step,id);
                        nl.produce(step + 1);
                        nr.produce(step + 1);
                        assert nl.id != nr.id : "on seg: " + id;
                        if (ALL_) {
                            neighborLeft.signalAll();
                            neighborRight.signalAll();
                        } else {
                            neighborLeft.signal();
                            neighborRight.signal();
                        }
                        runStep(step + 1);

                        return true;
                    }
                });
                return true;
            }
        });
    }
}

public class March {

    public static void main(String[] args) {
        Test.requireAssert();
        System.out.println("Segment.N_SEGS=" + Segment.N_SEGS);
        final List<Segment> segs = new ArrayList<>();
        for (int i = 0; i < Segment.N_SEGS; i++) {
            segs.add(new Segment(i));
        }

        for (int i = 0; i < Segment.N_SEGS; i++) {
            Segment seg = segs.get(i);
            if (i == 0) {
                seg.left = seg.neighborLeft = new GuardVar<ProdCon>(new ProdCon(true));
            } else {
                seg.neighborLeft = segs.get(i - 1).right;
            }

            if (i + 1 == Segment.N_SEGS) {
                seg.right = seg.neighborRight = new GuardVar<ProdCon>(new ProdCon(true));
            } else {
                seg.neighborRight = segs.get(i + 1).left;
            }

            System.out.println("seg: " + seg);
        }

        for (int i = 0; i < Segment.N_SEGS; i++) {
            final Segment seg = segs.get(i);
            seg.runStep(0);
        }

        segs.stream().map(s -> s.done).forEach(CompletableFuture::join);

        assert Segment.complete;
    }
}
*/
