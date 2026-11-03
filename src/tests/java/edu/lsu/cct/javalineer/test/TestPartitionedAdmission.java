package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.Pool;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A runPartitioned call is admitted once per list, not once per part (PartCall.java):
 * <ul>
 * <li>while its parts run, each list holds one range for the whole call;</li>
 * <li>calls which cross on two lists (one reads x and writes y, the other reads y and writes x) never deadlock, since
 * a call's lists are admitted one at a time in a fixed order, so neither can hold one list while the other holds the
 * second (with each list admitted independently, each could take its read and wait for the other's);</li>
 * <li>calls on the same lists still exclude each other: a writer never runs alongside another call's reader or
 * writer of the same range;</li>
 * <li>reducePartitioned reduces the parts' results in part order, so a non-commutative reduction is deterministic.</li>
 * </ul>
 */
public class TestPartitionedAdmission {
    static final long TIMEOUT_SECONDS = 60;
    static int checks = 0;

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    // Needs only one part running, so it passes under any pool size (with the per-part admission, every part's
    // ranges were taken at the call, before any part ran, so one running part is enough to tell the two apart).
    static void oneRangePerCall() throws Exception {
        int nParts = 8;
        var a = PartitionableList.of(80, i -> i);
        var b = PartitionableList.of(80, i -> 0);
        var running = new CountDownLatch(1);
        var letIn = new CountDownLatch(1);
        var fut = new CompletableFuture<Void>();
        var t = new Thread(() -> PartitionableList.runPartitioned(nParts, a.read(), b.write(), (av, bv) -> {
            running.countDown();
            try {
                letIn.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < bv.writableSize(); i++) {
                bv.set(i, av.get(i) * 2);
            }
        }).whenComplete((v, e) -> fut.complete(null)));
        t.setDaemon(true);
        t.start();
        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "a part runs");
        // The parts are blocked inside the call or queued, so the accountants are quiet: one range each
        check(TestPartitionedRelease.heldRanges(a) == 1, "a holds one range for the call, got "
                + TestPartitionedRelease.heldRanges(a));
        check(TestPartitionedRelease.heldRanges(b) == 1, "b holds one range for the call, got "
                + TestPartitionedRelease.heldRanges(b));
        letIn.countDown();
        fut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "nothing held after the call");
    }

    static void crossingCalls() throws Exception {
        int rounds = 2000;
        var x = PartitionableList.of(64, i -> 1);
        var y = PartitionableList.of(64, i -> 1);
        // Inside a call, the number of calls writing the list; a reader or writer seeing another writer is a fault.
        var writingX = new AtomicInteger();
        var writingY = new AtomicInteger();
        var overlaps = new AtomicInteger();
        var futs = new ArrayList<CompletableFuture<Void>>();
        var start = new CountDownLatch(1);
        var issued = new CountDownLatch(2);
        for (int side = 0; side < 2; side++) {
            final boolean xToY = side == 0;
            var t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                var mine = new ArrayList<CompletableFuture<Void>>();
                for (int r = 0; r < rounds; r++) {
                    var from = xToY ? x : y;
                    var to = xToY ? y : x;
                    var writingFrom = xToY ? writingX : writingY;
                    var writingTo = xToY ? writingY : writingX;
                    mine.add(PartitionableList.runPartitioned(4, from.read(), to.write(), (fv, tv) -> {
                        writingTo.incrementAndGet();
                        if (writingFrom.get() != 0 || writingTo.get() > 4) {
                            overlaps.incrementAndGet();
                        }
                        for (int i = 0; i < tv.writableSize(); i++) {
                            tv.set(i, fv.get(i));
                        }
                        writingTo.decrementAndGet();
                    }));
                }
                synchronized (futs) {
                    futs.addAll(mine);
                }
                issued.countDown();
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        check(issued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "both sides issue their calls");
        CompletableFuture.allOf(futs.toArray(CompletableFuture[]::new)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(overlaps.get() == 0, "no call ran alongside a conflicting one, got " + overlaps.get());
    }

    static void reduceInPartOrder() throws Exception {
        var digits = PartitionableList.of(10, i -> String.valueOf(i));
        for (int round = 0; round < 50; round++) {
            var joined = digits.reducePartitioned(10, view -> {
                // Part 0 is the slowest, so its result is the last in; the order must still be by part
                if (view.readableSize() == 1 && view.get(0).equals("0")) {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                var sb = new StringBuilder();
                for (int i = 0; i < view.readableSize(); i++) {
                    sb.append(view.get(i));
                }
                return CompletableFuture.completedFuture(sb.toString());
            }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(joined.equals("0123456789"), "the reduction is in part order, got " + joined);
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        // An argument (0 to 2) runs just that case
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        if (only < 0 || only == 0) oneRangePerCall();
        if (only < 0 || only == 1) crossingCalls();
        if (only < 0 || only == 2) reduceInPartOrder();
        System.out.println("TestPartitionedAdmission passed (" + checks + " checks)");
        System.exit(0);
    }
}
