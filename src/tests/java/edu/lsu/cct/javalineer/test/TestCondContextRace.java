package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stress test for the CondContext waiter list. Many threads add conditions that become true only once a final
 * value is set, while another thread repeatedly calls signalAll. A waiter added concurrently with a signalAll
 * iteration must never be lost (for example by a swallowed ConcurrentModificationException).
 */
public class TestCondContextRace {
    static final int THREADS = 4;
    static final int PER_THREAD = 150;
    static final int ROUNDS = 20;
    static final long TIMEOUT_SECONDS = 60;

    static void round() throws Exception {
        var gv = new GuardVar<>(0);
        var cond = Guard.newCondition(gv);
        var futures = new ArrayList<CompletableFuture<Void>>();
        var all = new ArrayList<List<CompletableFuture<Void>>>();
        var workers = new ArrayList<Thread>();
        var stop = new boolean[1];

        for (int t = 0; t < THREADS; t++) {
            var mine = new ArrayList<CompletableFuture<Void>>();
            all.add(mine);
            var th = new Thread(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    mine.add(Guard.runCondition(cond, v -> v.get() == 1));
                }
            });
            workers.add(th);
        }
        var signaler = new Thread(() -> {
            while (!stop[0]) {
                cond.signalAll();
                java.util.concurrent.locks.LockSupport.parkNanos(200_000);
            }
        });

        signaler.start();
        workers.forEach(Thread::start);
        for (var th : workers) {
            th.join();
        }
        // Every condition has been submitted; now make the predicate true and keep signaling.
        Guard.runGuarded(gv, v -> v.set(1)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            cond.signalAll();
        }
        all.forEach(futures::addAll);

        int incomplete = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        for (var f : futures) {
            long left = Math.max(1, deadline - System.nanoTime());
            try {
                f.get(left, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                incomplete++;
                // A lost wakeup may be repaired by later signals; give it one more chance, then count it.
            }
        }
        stop[0] = true;
        signaler.join();
        if (incomplete != 0) {
            throw new AssertionError("FAILED: " + incomplete + " of " + futures.size()
                    + " conditions never completed (lost wakeup)");
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        for (int r = 0; r < ROUNDS; r++) {
            round();
        }
        System.out.println("TestCondContextRace: all " + ROUNDS + " rounds passed");
    }
}
