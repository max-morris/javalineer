package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.CountdownLatch;
import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Loop;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool.join on programs that complete normally behaves exactly like CompletableFuture.join: same value, same
 * CompletionException for a failure, no deadlock reported, interrupt status preserved.
 */
public class TestJoinNormal {
    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static CompletableFuture<Integer> fib(int n) {
        if (n < 2) {
            return CompletableFuture.completedFuture(n);
        }
        var a = Pool.run(() -> fib(n - 1));
        var b = Pool.run(() -> fib(n - 2));
        return a.thenCombine(b, Integer::sum);
    }

    static int checks = 0;

    public static void main(String[] args) {
        Test.requireAssert();

        // A completed future.
        check(Pool.join(CompletableFuture.completedFuture(5)) == 5, "completed future");
        checks++;

        // Many short joins: the pool goes idle right after each future completes, which must never be mistaken
        // for a deadlock.
        for (int i = 0; i < 20000; i++) {
            final int k = i;
            check(Pool.join(Pool.run(() -> CompletableFuture.completedFuture(k))) == k, "short join " + i);
            checks++;
        }

        // A tree of tasks and continuations.
        check(Pool.join(fib(20)) == 6765, "fib(20)");
        checks++;

        // Guarded updates, a latch, and a parallel loop.
        var counter = new GuardVar<>(0);
        var latch = new CountdownLatch(1000);
        for (int i = 0; i < 1000; i++) {
            Pool.run(() -> counter.runGuarded(c -> {
                c.set(c.get() + 1);
                latch.signal();
            }));
        }
        Pool.join(latch.getFut());
        var seen = new AtomicInteger();
        Pool.join(Guard.runGuarded(counter, c -> seen.set(c.get())));
        check(seen.get() == 1000, "guarded counter is 1000, was " + seen.get());
        checks++;
        var sum = new AtomicInteger();
        Pool.join(Loop.parFor(0, 1000, i -> Pool.run(() -> { sum.addAndGet(i); })));
        check(sum.get() == 499500, "parFor sum");
        checks++;

        // The same with tracking on (condition tasks are registered and unregistered as they complete).
        Pool.setTrackPending(true);
        var latch2 = new CountdownLatch(100);
        for (int i = 0; i < 100; i++) {
            Pool.run(latch2::signal);
        }
        Pool.join(latch2.getFut());
        Pool.setTrackPending(false);
        checks++;

        // A failure is reported exactly as join reports it.
        var boom = new RuntimeException("boom");
        try {
            Pool.join(Pool.run(() -> {
                throw boom;
            }));
            throw new AssertionError("FAILED: a failed future joined normally");
        } catch (CompletionException e) {
            check(e.getCause() == boom, "the CompletionException carries the task's exception");
        }
        checks++;

        // An interrupt doesn't end the wait, and the interrupt status is set again on return.
        var slow = Pool.run(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return CompletableFuture.completedFuture(7);
        });
        Thread.currentThread().interrupt();
        check(Pool.join(slow) == 7, "join across an interrupt");
        check(Thread.interrupted(), "interrupt status preserved");
        checks++;

        // Another executor (tasks still in flight on the old one keep counting), then back.
        var saved = Pool.getPool();
        var cached = Executors.newCachedThreadPool();
        Pool.setPool(cached, false);
        check(Pool.join(fib(15)) == 610, "fib(15) on a cached pool");
        Pool.setPool(saved, false);
        cached.shutdown();
        check(Pool.join(fib(15)) == 610, "fib(15) back on the default pool");
        checks++;

        System.out.println("TestJoinNormal: all " + checks + " checks passed");
        System.exit(0);
    }
}
