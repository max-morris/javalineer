package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.CountdownLatch;
import edu.lsu.cct.javalineer.Pool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Signaling a CountdownLatch more times than its count is reported to the task failure handler, once per extra signal;
 * signals within the count, from any number of threads, are not.
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

        // Two more: two reports, each an IllegalStateException.
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

        // Without a handler, an extra signal is ignored, as before.
        Pool.setTaskFailureHandler(null);
        none.signal();

        System.out.println("TestLatchOverSignal passed");
        System.exit(0);
    }
}
