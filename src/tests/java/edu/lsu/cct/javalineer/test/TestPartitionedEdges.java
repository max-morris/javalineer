package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.PartitionableListExt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Edge cases of partitioned calls (PartCall.java):
 * <ul>
 * <li>a call of zero parts runs nothing, takes nothing and completes normally (runPartitioned and runPartitionedFut;
 * reducePartitioned reduces the empty list of part results); a negative part count is an IllegalArgumentException;
 * </li>
 * <li>a list appearing twice in one call with a write intent is an IllegalArgumentException (the call's second
 * admission would conflict with its own first one and wait forever); twice with read intents is fine;</li>
 * <li>two calls writing an empty list at once both complete (an empty write range is not recorded, so the second
 * one no longer trips RangeAccountant's assertion).</li>
 * </ul>
 * Run one case with its number (0 to 2) as the argument.
 */
public class TestPartitionedEdges {
    static final long TIMEOUT_SECONDS = 30;
    static int checks = 0;

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static Throwable thrownBy(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    static void zeroParts() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var b = PartitionableList.of(16, i -> 0);
        var ran = new AtomicInteger();
        PartitionableList.runPartitioned(0, a.read(), b.write(), (av, bv) -> {
            ran.incrementAndGet();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableListExt.runPartitionedFut(0, a.read(), b.write(), (av, bv) -> {
            ran.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ran.get() == 0, "a zero-part call runs no part");
        int parts = a.reducePartitioned(0, view -> {
            return CompletableFuture.completedFuture(view.readableSize());
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(parts == 0, "a zero-part reduction reduces the empty list of part results, got " + parts);
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "nothing held after zero-part calls");
        var negative = thrownBy(() -> PartitionableList.runPartitioned(-1, a.read(), b.write(), (av, bv) -> { }));
        check(negative instanceof IllegalArgumentException,
              "a negative part count is an IllegalArgumentException, got " + negative);
    }

    static void sameListTwice() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var b = PartitionableList.of(16, i -> 0);
        var t1 = thrownBy(() -> PartitionableList.runPartitioned(2, a.read(), a.write(), (r, w) -> { }));
        check(t1 instanceof IllegalArgumentException, "read and write of one list in a call is rejected, got " + t1);
        var t2 = thrownBy(() -> PartitionableList.runPartitioned(2, a.write(), b.read(), a.write(), (w1, r, w2) -> { }));
        check(t2 instanceof IllegalArgumentException, "two writes of one list in a call are rejected, got " + t2);
        var t3 = thrownBy(() -> PartitionableListExt.runPartitionedFut(2, a.read(), a.write(), (r, w) -> {
            return CompletableFuture.completedFuture(null);
        }));
        check(t3 instanceof IllegalArgumentException, "the Fut form rejects it too, got " + t3);
        var sum = new AtomicInteger();
        PartitionableList.runPartitioned(2, a.read(), a.read(), b.write(), (r1, r2, w) -> {
            for (int i = 0; i < w.writableSize(); i++) {
                w.set(i, r1.get(i) + r2.get(i));
                sum.addAndGet(r1.get(i) + r2.get(i));
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(sum.get() == 2 * (15 * 16 / 2), "two reads of one list in a call run, got " + sum.get());
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "nothing held after the calls");
    }

    static void emptyList() throws Exception {
        var e = PartitionableList.of(0, i -> 0);
        var running = new CountDownLatch(1);
        var letIn = new CountDownLatch(1);
        var first = TestPartitionedRelease.start(() -> PartitionableList.runPartitioned(1, e.write(), v -> {
            running.countDown();
            try {
                letIn.await();
            } catch (InterruptedException ignored) {
            }
        }));
        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the first call's part runs");
        // Both calls hold an empty write range at once; neither writes anything
        TestPartitionedRelease.start(() -> PartitionableList.runPartitioned(1, e.write(), v -> { }))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        letIn.countDown();
        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // More parts than cells: empty parts within one call
        var small = PartitionableList.of(2, i -> 0);
        PartitionableList.runPartitioned(5, small.write(), v -> {
            for (int i = 0; i < v.writableSize(); i++) {
                v.set(i, 1);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(e) == 0 && TestPartitionedRelease.heldRanges(small) == 0,
              "nothing held after the calls");
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        if (only < 0 || only == 0) zeroParts();
        if (only < 0 || only == 1) sameListTwice();
        if (only < 0 || only == 2) emptyList();
        System.out.println("TestPartitionedEdges passed (" + checks + " checks)");
        System.exit(0);
    }
}
