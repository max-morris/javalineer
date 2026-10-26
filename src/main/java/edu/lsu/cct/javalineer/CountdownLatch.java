package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicInteger;

public class CountdownLatch extends Latch<Integer> {
    // The signals still expected. Once the latch's future completes, its condition is gone, so a further signal would
    // be ignored; this notices it.
    private final AtomicInteger remaining;

    public CountdownLatch(int n) {
        // n + 1 because runCondition will immediately decrement by 1
        super(n + 1, counter -> {
            counter.set(counter.get() - 1);
            return counter.get() == 0;
        });
        remaining = new AtomicInteger(n);
    }

    /**
     * A signal beyond the latch's count is a programming error: whoever waited for the latch may already have gone on
     * before the work behind this signal was done. It is reported to the task failure handler (see
     * {@link Pool#setTaskFailureHandler}), and otherwise ignored, as it always was.
     */
    @Override
    protected void beforeSignal() {
        if (remaining.getAndDecrement() <= 0) {
            Pool.reportTaskFailure(new IllegalStateException(
                    "CountdownLatch signaled more times than its count"));
        }
    }
}
