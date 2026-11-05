package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A signalAll's walk of the waiter list must never race the appends of conditions which start waiting meanwhile.
 * Like TestCondContextRace, but every predicate check is slow, so each signalAll iterates for milliseconds while new
 * waiters are issued. Written when the list had a guard of its own and a signal walked a snapshot of it: a walk
 * sharing the live list failed with a ConcurrentModificationException (reported to the task failure handler) or
 * skipped a waiter. The list is now walked and appended to only under the condition's guards, so the appends queue
 * behind the walk; the test still checks that no walk fails and no waiter is lost.
 */
public class TestCondSnapshot {
    static final int THREADS = 4;
    static final int PER_THREAD = 100;
    static final int SIGNALERS = 2;
    static final int ROUNDS = 100;
    static final long TIMEOUT_SECONDS = 30;

    static volatile long sink;

    static boolean slowCheck(int v) {
        long x = v;
        for (int i = 0; i < 2_000; i++) {
            x = x * 6364136223846793005L + 1442695040888963407L;
        }
        sink = x;
        return v == 1;
    }

    static void round(ConcurrentLinkedQueue<Throwable> failures) throws Exception {
        var gv = new GuardVar<>(0);
        var cond = Guard.newCondition(gv);
        var all = new ArrayList<List<CompletableFuture<Void>>>();
        var workers = new ArrayList<Thread>();
        var stop = new java.util.concurrent.atomic.AtomicBoolean();

        for (int t = 0; t < THREADS; t++) {
            var mine = new ArrayList<CompletableFuture<Void>>();
            all.add(mine);
            workers.add(new Thread(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    mine.add(Guard.runCondition(cond, v -> slowCheck(v.get())));
                    if (i % 5 == 4) {
                        // Spread the appends over many signalAll iterations.
                        java.util.concurrent.locks.LockSupport.parkNanos(500_000);
                    }
                }
            }));
        }
        var signalers = new ArrayList<Thread>();
        for (int t = 0; t < SIGNALERS; t++) {
            signalers.add(new Thread(() -> {
                while (!stop.get()) {
                    cond.signalAll();
                    // Throttle: wait for gv once (roughly, until the signal's iteration has run), so that the
                    // signals do not pile up without bound.
                    Guard.runGuarded(gv, v -> { }).join();
                }
            }));
        }

        signalers.forEach(Thread::start);
        workers.forEach(Thread::start);
        for (var th : workers) {
            th.join();
        }
        Guard.runGuarded(gv, v -> v.set(1)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        cond.signalAll();

        int incomplete = 0, total = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        for (var mine : all) {
            for (var f : mine) {
                total++;
                while (true) {
                    if (!failures.isEmpty()) {
                        stop.set(true);
                        throw new AssertionError("FAILED: a signal's iteration failed: " + failures.peek(),
                                failures.peek());
                    }
                    try {
                        f.get(100, TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException e) {
                        if (System.nanoTime() > deadline) {
                            incomplete++;
                            break;
                        }
                    }
                }
            }
        }
        stop.set(true);
        for (var th : signalers) {
            th.join();
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("FAILED: a signal's iteration failed: " + failures.peek(), failures.peek());
        }
        if (incomplete != 0) {
            throw new AssertionError("FAILED: " + incomplete + " of " + total + " conditions never completed");
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        Pool.setTaskFailureHandler(failures::add);
        for (int r = 0; r < ROUNDS; r++) {
            round(failures);
        }
        System.out.println("TestCondSnapshot: all " + ROUNDS + " rounds passed");
    }
}
