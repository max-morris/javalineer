package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A latch whose future completes after its count of signals. The count is an atomic integer: a signal is one
 * decrement, and the signal which takes it to zero completes the future, on the signaling thread. No guards, no
 * condition, no copies. Each decrement is a read-modify-write of the one count, so everything a signaler did before
 * its signal happens-before the future's completion, as it did when the count was a guarded variable.
 */
public class CountdownLatch extends Latch<Integer> {
    private final int count;
    // The signals still expected; negative once the latch has been signaled more often than its count.
    private final AtomicInteger remaining;

    public CountdownLatch(int n) {
        super();
        count = checkCount(n);
        remaining = new AtomicInteger(n);
        if (n == 0) {
            complete();
        } else {
            // For a deadlock report (Pool.join): a latch signaled fewer times than its count waits forever.
            Pool.trackPending(getFut(), "latch", this);
        }
    }

    // A negative count would never reach zero, so the latch's future could never complete.
    private static int checkCount(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("A CountdownLatch's count can't be negative: " + n);
        }
        return n;
    }

    /**
     * A signal beyond the latch's count is a programming error: whoever waited for the latch may already have gone on
     * before the work behind this signal was done. It is reported to the task failure handler (see
     * {@link Pool#setTaskFailureHandler}), and otherwise ignored, as it always was.
     */
    @Override
    final void countDown() {
        int left = remaining.decrementAndGet();
        if (left == 0) {
            complete();
        } else if (left < 0) {
            Pool.reportTaskFailure(new IllegalStateException(
                    "CountdownLatch signaled more times than its count"));
        }
    }

    @Override
    public String toString() {
        return "CountdownLatch(" + Math.max(0, remaining.get()) + " of " + count + " signals to go)";
    }
}
