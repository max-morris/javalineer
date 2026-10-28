package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The completion of one runPartitioned or reducePartitioned call: a future which completes once every piece of work
 * added to the latch is over, shaped like Guardian's ProgressLatch (gu4.ProgressLatch, rule R-E16).
 * <p>
 * The count starts at 1, the seal's token, which the calling thread holds while it sets up the call's parts. Each
 * {@link #add} raises the count by 1 and lowers it again when the added future completes. Each {@link #hold} raises it
 * by 1 for work whose futures don't exist yet (a part which hasn't run): that work adds its futures and only then
 * lowers the count with {@link #arrive}, so a hold is an add of a future which completes once the work's own futures
 * have been added. {@link #seal} drops the seal's token and returns the latch's future, which completes when the count
 * reaches 0. Until the seal, the seal's token keeps the count above 0; after it, a hold is only ever taken by the
 * calling thread before the seal, and every add comes from work whose hold is still standing. So the future completes
 * exactly once, after the seal, when every hold has arrived and every added future has completed.
 * <p>
 * No guards, conditions or copies: an increment per add or hold, a decrement per completion, one completion of the
 * future. A failure (a part's task threw, or an added future completed exceptionally) is recorded, the first one wins,
 * and the latch's future then completes exceptionally with it, but still only when everything else is over too.
 */
final class ProgressLatch {
    private final AtomicInteger count = new AtomicInteger(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final CompletableFuture<Void> done = new CompletableFuture<>();

    /**
     * Waits for fut too. The caller must hold a token (the seal's or a hold's) while it adds.
     */
    void add(CompletableFuture<?> fut) {
        count.incrementAndGet();
        fut.whenComplete((value, t) -> {
            if (t != null) {
                fail(t);
            }
            arrive();
        });
    }

    /**
     * Raises the count for work which will add its futures later and then {@link #arrive}. Only before the seal.
     */
    void hold() {
        count.incrementAndGet();
    }

    /**
     * Records a failure of the call; the latch's future completes exceptionally with the first one.
     */
    void fail(Throwable t) {
        failure.compareAndSet(null, t);
    }

    /**
     * Drops one token: a hold's, an added future's, or (through {@link #seal}) the seal's.
     */
    void arrive() {
        int left = count.decrementAndGet();
        assert left >= 0 : "ProgressLatch arrived more often than it was held";
        if (left == 0) {
            var t = failure.get();
            if (t == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(t);
            }
        }
    }

    /**
     * Drops the seal's token: no more holds. Called once, by the thread which set the call up.
     */
    CompletableFuture<Void> seal() {
        arrive();
        return done;
    }
}
