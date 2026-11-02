package edu.lsu.cct.javalineer;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The admission, parts and release of one runPartitioned or reducePartitioned call.
 * <p>
 * The call takes one range per list, the union of its parts' ranges (for a list with nGhosts ghosts and parts covering
 * [lo, hi), the write range is [lo, hi) and the read range [lo - nGhosts, hi + nGhosts)), through one condition on
 * that list's RangeAccountant. Its parts then run with no further accounting, and the last part to finish releases
 * the call's ranges (one guarded release and signalAll per list) and only then completes the call's future, so the
 * future completes when every range of the call is free again. Per call that is L conditions and L releases for L
 * lists, instead of a condition and a release per part per list, and the RangeAccountants hold a range per call, not
 * per part.
 * <p>
 * What this gives up: a part of a later call can no longer start as soon as the parts of an earlier call which it
 * overlaps are over; it waits for the whole earlier call. Mutual exclusion is unchanged (the union covers every part).
 * <p>
 * The lists are admitted one after another in the order of their RangeAccountants' guards (a list appearing twice is
 * admitted twice, in a row), never all at once: a call holding some of its lists waits only for lists later in that
 * order, so calls which overlap on several lists cannot wait for each other in a cycle, which they could when every
 * list was admitted independently.
 */
final class PartCall {
    private final PartitionableList<?>[] lists;
    private final PartIntentKind[] kinds;
    private final int[] lo, hi;
    private final int nGhosts;
    private final int nParts;
    private final AtomicInteger remaining;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    // The lists in admission order, and how many of them have been admitted (written only by the admission chain)
    private final int[] order;
    private int admitted = 0;

    private PartCall(int nParts, int nGhosts, PartitionableList<?>[] lists, PartIntentKind[] kinds, int[] lo, int[] hi) {
        this.lists = lists;
        this.kinds = kinds;
        this.lo = lo;
        this.hi = hi;
        this.nGhosts = nGhosts;
        this.nParts = nParts;
        this.remaining = new AtomicInteger(nParts);
        this.order = new int[lists.length];
        var boxed = new Integer[lists.length];
        for (int j = 0; j < lists.length; j++) {
            boxed[j] = j;
        }
        // Stable, so a list appearing twice keeps its positions' order
        Arrays.sort(boxed, (a, b) -> lists[a].rangeAccountant.compareTo(lists[b].rangeAccountant));
        for (int j = 0; j < lists.length; j++) {
            order[j] = boxed[j];
        }
    }

    /**
     * A call of nChunks parts split by {@link PartitionableList#partIndex}: each list's parts cover
     * [partIndex(0), partIndex(nChunks)) of that list.
     */
    static PartCall ofChunks(int nChunks, int nGhosts, PartitionableList<?>[] lists, PartIntentKind[] kinds) {
        var lo = new int[lists.length];
        var hi = new int[lists.length];
        for (int j = 0; j < lists.length; j++) {
            int size = lists[j].data.size();
            lo[j] = PartitionableList.partIndex(0, nChunks, size, nGhosts);
            hi[j] = PartitionableList.partIndex(nChunks, nChunks, size, nGhosts);
        }
        return new PartCall(nChunks, nGhosts, lists, kinds, lo, hi);
    }

    /**
     * A call with the parts of ranges, the same in every list: the writable union is the smallest range containing
     * every part's writable range.
     */
    static PartCall ofRanges(PartitionRangeProvider ranges, int nGhosts, PartitionableList<?>[] lists,
                             PartIntentKind[] kinds) {
        int nParts = ranges.numPartitions();
        int begin = Integer.MAX_VALUE, end = Integer.MIN_VALUE;
        for (int p = 0; p < nParts; p++) {
            begin = Math.min(begin, ranges.getWritableBegin(p));
            end = Math.max(end, ranges.getWritableEnd(p));
        }
        var lo = new int[lists.length];
        var hi = new int[lists.length];
        Arrays.fill(lo, begin);
        Arrays.fill(hi, end);
        return new PartCall(nParts, nGhosts, lists, kinds, lo, hi);
    }

    int numParts() {
        return nParts;
    }

    /**
     * The call's future: it completes once every part has finished and every range of the call is released,
     * exceptionally with the first failure if there was one.
     */
    CompletableFuture<Void> future() {
        return done;
    }

    /**
     * Admits the call (every list, in order) and then starts its parts: part p runs parts.run(p). The parts run in
     * the pool, except the last, which runs on the thread that finished the admission when that is the calling
     * thread (the common case: no other call holds a conflicting range). When the admission waits, the thread that
     * finishes it is releasing another call's range inside that list's guard, so every part goes to the pool. Each
     * part must end with exactly one {@link #partDone}. With no parts, the call is over at once and takes nothing.
     */
    void start(PartRunner parts) {
        if (nParts == 0) {
            done.complete(null);
            return;
        }
        var admission = admitFrom(0);
        boolean inline = admission.isDone();
        admission.whenComplete((v, t) -> {
            if (t != null) {
                // A condition failed (it threw): nothing ran, so give back what was admitted and fail.
                failure.compareAndSet(null, t);
                release();
                return;
            }
            for (int p = 0; p < nParts; p++) {
                final int partNum = p;
                if (inline && p == nParts - 1) {
                    parts.run(partNum);
                } else {
                    try {
                        Pool.getPool().execute(() -> parts.run(partNum));
                    } catch (RejectedExecutionException e) {
                        partDone(e);
                    }
                }
            }
        });
    }

    @FunctionalInterface
    interface PartRunner {
        void run(int partNum);
    }

    private CompletableFuture<Void> admitFrom(int k) {
        if (k == order.length) {
            return CompletableFuture.completedFuture(null);
        }
        int j = order[k];
        var list = lists[j];
        var kind = kinds[j];
        int l = lo[j], h = hi[j];
        return Guard.runCondition(list.rangeAccountantCond, (rangeAccountantVar) -> {
            return rangeAccountantVar.get().isRangeOk(kind, l, h, nGhosts);
        }).thenCompose(v -> {
            admitted = k + 1;
            return admitFrom(k + 1);
        });
    }

    /**
     * A part is over, with t its failure or null. The last part releases the call's ranges and then completes the
     * call's future.
     */
    void partDone(Throwable t) {
        if (t != null) {
            failure.compareAndSet(null, t);
        }
        int left = remaining.decrementAndGet();
        assert left >= 0 : "a part of a partitioned call finished twice";
        if (left == 0) {
            release();
        }
    }

    // Releases the admitted lists' ranges and then completes the future. Each release goes through the list's guard
    // and may queue, so the future waits for all of them.
    private void release() {
        var releases = new CompletableFuture<?>[admitted];
        for (int k = 0; k < admitted; k++) {
            int j = order[k];
            var list = lists[j];
            var kind = kinds[j];
            int l = lo[j], h = hi[j];
            releases[k] = Guard.runGuarded(list.rangeAccountant, (rangeAccountantVar) -> {
                rangeAccountantVar.get().release(kind, l, h, nGhosts);
                list.rangeAccountantCond.signalAll();
            });
        }
        CompletableFuture.allOf(releases).whenComplete((v, e) -> {
            if (e != null) {
                failure.compareAndSet(null, e);
            }
            var t = failure.get();
            if (t == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(t);
            }
        });
    }
}
