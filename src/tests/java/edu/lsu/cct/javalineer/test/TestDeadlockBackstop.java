package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.CountdownLatch;
import edu.lsu.cct.javalineer.DeadlockException;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Pool.join must not hang when the awaited future can never complete: once the pool is idle, it reports the pending
 * state on stderr and throws DeadlockException. Each case below is a deadlock; the test passes when every one is
 * reported (and fails, rather than hanging, if any join waits longer than the watchdog allows).
 */
public class TestDeadlockBackstop {
    static final long WATCHDOG_MILLIS = 60_000;

    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    /** Joins fut, which must deadlock; returns the report. */
    static String expectDeadlock(String name, CompletableFuture<?> fut) {
        long t0 = System.nanoTime();
        try {
            Object v = Pool.join(fut);
            throw new AssertionError("FAILED: " + name + ": join returned " + v + " instead of reporting a deadlock");
        } catch (DeadlockException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            check(!fut.isDone(), name + ": the future is still incomplete");
            check(e.getReport().contains("deadlock"), name + ": the report names the deadlock");
            System.out.println(name + ": deadlock reported after " + ms + " ms");
            return e.getReport();
        }
    }

    public static void main(String[] args) {
        Test.requireAssert();

        var watchdog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            System.err.println("TestDeadlockBackstop: FAILED: a join hung for " + WATCHDOG_MILLIS + " ms");
            System.exit(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        // 1. The Promise shape (hole 29): a future completed only by a continuation of itself.
        {
            var p = new CompletableFuture<Integer>();
            p.thenAccept(v -> p.complete(v));
            expectDeadlock("self-cycle", p);
        }

        // 2. A cycle through two futures, set up inside a pool task after other work has run, so the pool is busy
        //    for a while before it goes idle.
        {
            Supplier<CompletableFuture<Integer>> body = () -> {
                var p1 = new CompletableFuture<Integer>();
                var p2 = new CompletableFuture<Integer>();
                p1.thenAccept(v -> p2.complete(v + 1));
                p2.thenAccept(v -> p1.complete(v + 1));
                CompletableFuture<Integer> work = CompletableFuture.completedFuture(0);
                for (int i = 0; i < 100; i++) {
                    final int k = i;
                    work = work.thenCompose(x -> Pool.run(() -> CompletableFuture.completedFuture(x + k)));
                }
                return work.thenCompose(x -> p1);
            };
            expectDeadlock("two-future cycle", Pool.run(body));
        }

        // 3. With tracking on: a latch signaled fewer times than its count, and a registered continuation. The
        //    report lists both, with where they were registered.
        {
            Pool.setTrackPending(true);
            var latch = new CountdownLatch(3);
            Pool.run(latch::signal);
            Pool.run(latch::signal);
            var p = new CompletableFuture<Integer>();
            Pool.trackPending(p, "continuation completing its own future", "p");
            p.thenAccept(v -> p.complete(v));
            var report = expectDeadlock("under-signaled latch", latch.getFut().thenCombine(p, (a, b) -> b));
            check(report.contains("condition task"), "the report lists the latch's condition task");
            check(report.contains("CountdownLatch.<init>"), "the report shows where the latch was created");
            check(report.contains("continuation completing its own future of p"),
                  "the report lists the registered continuation");
            Pool.setTrackPending(false);
        }

        // 4. A rejected submission must not leave the task count raised (which would disable detection).
        {
            Executor rejecting = r -> {
                throw new RejectedExecutionException("rejected");
            };
            var saved = Pool.getPool();
            Pool.setPool(rejecting, false);
            try {
                Pool.run(() -> { });
                throw new AssertionError("FAILED: the rejecting pool accepted a task");
            } catch (RejectedExecutionException expected) {
            } finally {
                Pool.setPool(saved, false);
            }
            var p = new CompletableFuture<Integer>();
            p.thenAccept(v -> p.complete(v));
            expectDeadlock("after a rejection", p);
        }

        System.out.println("TestDeadlockBackstop: all 4 deadlocks reported");
        System.exit(0);
    }
}
