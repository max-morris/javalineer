package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.CountdownLatch;
import edu.lsu.cct.javalineer.Pool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Signaling a CountdownLatch more times than its count is reported to the task failure handler, once per extra signal;
 * signals within the count, from any number of threads, are not. A negative count is rejected, and an empty parFor
 * range (which once made one) completes at once.
 */
public class TestLatchOverSignal {
    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        List<Throwable> reports = new CopyOnWriteArrayList<>();
        Pool.setTaskFailureHandler(reports::add);

        // Exactly the count, from many threads: no report.
        int n = 64;
        var latch = new CountdownLatch(n);
        var started = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            Pool.run(() -> {
                started.countDown();
                latch.signal();
            });
        }
        latch.getFut().get(30, TimeUnit.SECONDS);
        check(started.await(30, TimeUnit.SECONDS), "every signaler ran");
        check(reports.isEmpty(), "no report for signals within the count, got " + reports);

        // The future completes at the count's last signal, not before, and once.
        var three = new CountdownLatch(3);
        var completions = new java.util.concurrent.atomic.AtomicInteger();
        three.getFut().thenRun(completions::incrementAndGet);
        three.signal();
        three.signal();
        check(!three.getFut().isDone(), "a latch counting 3 is incomplete after 2 signals");
        three.signal();
        check(three.getFut().isDone(), "a latch counting 3 is complete after its third signal, on the signaling thread");
        check(completions.get() == 1, "its dependents ran once");
        check(reports.isEmpty(), "no report for signals within the count of a second latch, got " + reports);

        // Everything a signaler did before its signal is visible once the future completes.
        int m = 1000;
        var flags = new boolean[m];
        var hb = new CountdownLatch(m);
        for (int i = 0; i < m; i++) {
            final int k = i;
            Pool.run(() -> {
                flags[k] = true;
                hb.signal();
            });
        }
        var all = hb.getFut().thenApply(v -> {
            for (boolean f : flags) {
                if (!f) {
                    return false;
                }
            }
            return true;
        }).get(30, TimeUnit.SECONDS);
        check(all, "every signaler's write happens-before the latch's completion");

        // Two more on the first latch: two reports, each an IllegalStateException.
        latch.signal();
        latch.signal();
        check(reports.size() == 2, "two reports for two extra signals, got " + reports.size());
        check(reports.stream().allMatch(t -> t instanceof IllegalStateException), "the reports are IllegalStateExceptions");

        // A latch which needs no signals: any signal is extra.
        reports.clear();
        var none = new CountdownLatch(0);
        none.getFut().get(30, TimeUnit.SECONDS);
        none.signal();
        check(reports.size() == 1, "a signal of a latch counting 0 is reported");

        // A negative count is rejected.
        try {
            new CountdownLatch(-1);
            check(false, "a negative count is rejected");
        } catch (IllegalArgumentException expected) {
        }

        // An empty parFor range runs nothing and completes.
        edu.lsu.cct.javalineer.Loop.parFor(5, 0, i -> java.util.concurrent.CompletableFuture.completedFuture(null))
                                   .get(30, TimeUnit.SECONDS);

        // Without a handler, an extra signal is ignored, as before.
        Pool.setTaskFailureHandler(null);
        none.signal();

        System.out.println("TestLatchOverSignal passed");
        System.exit(0);
    }
}
