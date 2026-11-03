package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.PartitionableListExt;
import edu.lsu.cct.javalineer.Pool;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * runPartitionedFut is admitted as runPartitioned is (PartCall.java: one range per list, the lists in guard order):
 * <ul>
 * <li>a runPartitionedFut call crossing a runPartitioned call, or another runPartitionedFut call, on two lists (one
 * reads x and writes y, the other reads y and writes x) never deadlocks, in either argument order; with the old
 * per-part admission it did (the review's ReviewMix shapes), since a Fut part took its y range while its x range
 * waited for the other call, which held x and waited for y;</li>
 * <li>the call's future completes only when its ranges are released and every part's future is complete;</li>
 * <li>the ranges are released when the last part's body returns, before the parts' futures complete (the documented
 * contract of PartitionableListExt), so a part can return the future of a call on a sub-range of the same list:
 * a merge sort recursing on the halves of its range completes.</li>
 * </ul>
 * Run one case with its number (0 to 3) as the argument.
 */
public class TestPartitionedFut {
    static final long TIMEOUT_SECONDS = 60;
    static final long SETTLE_MILLIS = 300;
    static int checks = 0;

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    /**
     * Crossing calls: side A reads x and writes y, side B reads y and writes x, each side a runPartitionedFut call
     * (aFut, bFut) or a runPartitioned one. x is made first, so its guard comes first in the admission order: side
     * A's argument order agrees with it and side B's does not. A Fut part returns, in turn, a completed future or
     * one completed later by a pool task (which does not touch the views).
     */
    static void crossing(boolean aFut, boolean bFut, int nParts, int rounds) throws Exception {
        String name = (aFut ? "fut" : "c3") + "/" + (bFut ? "fut" : "c3") + " with " + nParts + " parts";
        var x = PartitionableList.of(64, i -> 1);
        var y = PartitionableList.of(64, i -> 1);
        // Inside a part, the number of parts writing the list; a part seeing another call's writer is a fault.
        var writingX = new AtomicInteger();
        var writingY = new AtomicInteger();
        var overlaps = new AtomicInteger();
        var futs = new ArrayList<CompletableFuture<Void>>();
        var start = new CountDownLatch(1);
        var issued = new CountDownLatch(2);
        for (int side = 0; side < 2; side++) {
            final boolean xToY = side == 0;
            final boolean fut = xToY ? aFut : bFut;
            var t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                var from = xToY ? x : y;
                var to = xToY ? y : x;
                var writingFrom = xToY ? writingX : writingY;
                var writingTo = xToY ? writingY : writingX;
                var mine = new ArrayList<CompletableFuture<Void>>();
                for (int r = 0; r < rounds; r++) {
                    final boolean deferred = r % 2 == 1;
                    if (fut) {
                        mine.add(PartitionableListExt.runPartitionedFut(nParts, from.read(), to.write(), (fv, tv) -> {
                            copy(fv, tv, writingFrom, writingTo, overlaps, nParts);
                            return deferred ? Pool.run(() -> { }) : CompletableFuture.completedFuture(null);
                        }));
                    } else {
                        mine.add(PartitionableList.runPartitioned(nParts, from.read(), to.write(), (fv, tv) -> {
                            copy(fv, tv, writingFrom, writingTo, overlaps, nParts);
                        }));
                    }
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
        check(issued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), name + ": both sides issue their calls");
        CompletableFuture<?>[] all;
        synchronized (futs) {
            all = futs.toArray(CompletableFuture[]::new);
        }
        CompletableFuture.allOf(all).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(overlaps.get() == 0, name + ": no call ran alongside a conflicting one, got " + overlaps.get());
        check(TestPartitionedRelease.heldRanges(x) == 0 && TestPartitionedRelease.heldRanges(y) == 0,
              name + ": nothing held after the calls");
    }

    static void copy(edu.lsu.cct.javalineer.ReadOnlyPartListView<Integer> fv,
                     edu.lsu.cct.javalineer.WriteOnlyPartListView<Integer> tv,
                     AtomicInteger writingFrom, AtomicInteger writingTo, AtomicInteger overlaps, int nParts) {
        writingTo.incrementAndGet();
        if (writingFrom.get() != 0 || writingTo.get() > nParts) {
            overlaps.incrementAndGet();
        }
        for (int i = 0; i < tv.writableSize(); i++) {
            tv.set(i, fv.get(i));
        }
        writingTo.decrementAndGet();
    }

    static void crossingCases() throws Exception {
        for (int nParts : new int[] {1, 4}) {
            crossing(false, true, nParts, 2000);
            crossing(true, false, nParts, 2000);
            crossing(true, true, nParts, 2000);
        }
    }

    /**
     * The call's future waits for the releases: the part's body blocks until let in and returns a completed future;
     * the test holds a's guard while the body returns, so the release queues behind it, and the call must not be
     * over until the guard is let go. Before the port, the future completed when the parts' futures did.
     */
    static void futureAfterRelease() throws Exception {
        var a = PartitionableList.of(64, i -> i);
        var b = PartitionableList.of(64, i -> 0);
        var running = new CountDownLatch(1);
        var letIn = new CountDownLatch(1);
        var fut = TestPartitionedRelease.start(() -> PartitionableListExt.runPartitionedFut(1, a.read(), b.write(),
                (av, bv) -> {
                    running.countDown();
                    try {
                        letIn.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for (int i = 0; i < bv.writableSize(); i++) {
                        bv.set(i, av.get(i));
                    }
                    return CompletableFuture.completedFuture(null);
                }));
        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the part runs");
        var letGo = TestPartitionedRelease.holdGuard(a);
        letIn.countDown();
        Thread.sleep(SETTLE_MILLIS);
        check(!fut.isDone(), "the Fut call isn't over while its release waits for the list's guard");
        letGo.countDown();
        fut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "nothing held after the Fut call");
    }

    /**
     * The early release, as documented: once the part's body has returned, a later writer of the same list is
     * admitted and completes while the part's future is still pending; the Fut call completes only after that
     * future does.
     */
    static void earlyRelease() throws Exception {
        var a = PartitionableList.of(16, i -> 0);
        var pending = new CompletableFuture<Void>();
        var returned = new CountDownLatch(1);
        var fut = TestPartitionedRelease.start(() -> PartitionableListExt.runPartitionedFut(1, a.readWrite(), v -> {
            returned.countDown();
            return pending;
        }));
        check(returned.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the Fut part's body runs");
        PartitionableList.runPartitioned(1, a.write(), v -> {
            for (int i = 0; i < v.writableSize(); i++) {
                v.set(i, 2);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(!fut.isDone(), "the Fut call is not over while its part's future is pending");
        pending.complete(null);
        fut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(a) == 0, "nothing held after the Fut call");
    }

    // The halves of [lo, hi), the shape of MergeSort's generated calls
    static PartitionRangeProvider halves(int lo, int hi) {
        int mid = (lo + hi) / 2;
        return new PartitionRangeProvider() {
            public int numPartitions() {
                return 2;
            }

            public int getWritableBegin(int p) {
                return p == 0 ? lo : mid;
            }

            public int getWritableEnd(int p) {
                return p == 0 ? mid : hi;
            }
        };
    }

    static PartitionRangeProvider whole(int lo, int hi) {
        return new PartitionRangeProvider() {
            public int numPartitions() {
                return 1;
            }

            public int getWritableBegin(int p) {
                return lo;
            }

            public int getWritableEnd(int p) {
                return hi;
            }
        };
    }

    // Sorts [lo, hi) of the list: each half's part returns the future of the sort of its half (a call on a sub-range
    // of the same list, admitted once the enclosing call's parts have returned), then the halves are merged.
    static CompletableFuture<Void> sort(PartitionableList<Integer> list, int lo, int hi) {
        if (hi - lo < 2) {
            return CompletableFuture.completedFuture(null);
        }
        int mid = (lo + hi) / 2;
        return PartitionableListExt.runPartitionedFut(halves(lo, hi), list.readWrite(), v -> {
            return v.partitionNum() == 0 ? sort(list, lo, mid) : sort(list, mid, hi);
        }).thenCompose(ignored -> PartitionableList.runPartitioned(whole(lo, hi), list.readWrite(), v -> {
            int n = v.writableSize(), m = mid - lo;
            var merged = new int[n];
            int i = 0, j = m, k = 0;
            while (i < m || j < n) {
                if (j >= n || (i < m && v.get(i) <= v.get(j))) {
                    merged[k++] = v.get(i++);
                } else {
                    merged[k++] = v.get(j++);
                }
            }
            for (k = 0; k < n; k++) {
                v.set(k, merged[k]);
            }
        }));
    }

    static void mergeSort() throws Exception {
        var rnd = new Random(7);
        for (int round = 0; round < 20; round++) {
            int n = 1 + rnd.nextInt(300);
            var list = PartitionableList.of(n, i -> rnd.nextInt(1000));
            long sum = 0;
            for (int i = 0; i < n; i++) {
                sum += peek(list, i);
            }
            sort(list, 0, n).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long after = 0;
            for (int i = 0; i < n; i++) {
                after += peek(list, i);
                check(i == 0 || peek(list, i - 1) <= peek(list, i), "sorted at " + i + " of " + n);
            }
            check(sum == after, "the sort keeps the elements");
            check(TestPartitionedRelease.heldRanges(list) == 0, "nothing held after the sort");
        }
    }

    // Reads an element through a one-part read call (the list's storage is package-private)
    static int peek(PartitionableList<Integer> list, int i) throws Exception {
        var out = new int[1];
        PartitionableList.runPartitioned(whole(i, i + 1), list.read(), v -> {
            out[0] = v.get(0);
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return out[0];
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        if (only < 0 || only == 0) crossingCases();
        if (only < 0 || only == 1) futureAfterRelease();
        if (only < 0 || only == 2) earlyRelease();
        if (only < 0 || only == 3) mergeSort();
        System.out.println("TestPartitionedFut passed (" + checks + " checks)");
        System.exit(0);
    }
}
