package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck1;
import edu.lsu.cct.javalineer.functionalinterfaces.CondTask1;

import java.util.concurrent.CompletableFuture;

public class Latch<T> {
    // Null for a latch which does its own counting (CountdownLatch): its signal is countDown, not a condition.
    private final CondContext<CondTask1<T>> cond;
    private final CompletableFuture<Void> fut;

    public Latch(T initial, CondCheck1<T> task) {
        cond = CondContext.newCond(new GuardVar<>(initial));
        fut = Guard.runCondition(cond, task);
    }

    /**
     * A latch without a condition: each signal calls {@link #countDown}, which completes the latch with
     * {@link #complete}.
     */
    Latch() {
        cond = null;
        fut = new CompletableFuture<>();
    }

    public final void signal() {
        beforeSignal();
        if (cond != null) {
            cond.signal();
        } else {
            countDown();
        }
    }

    /**
     * Called on the signaling thread before each signal is delivered, e.g. to check that the latch isn't signaled
     * more often than it expects.
     */
    protected void beforeSignal() {
    }

    /**
     * The signal of a latch made without a condition. Only such a latch's class (CountdownLatch) overrides it.
     */
    void countDown() {
        throw new AssertionError("a latch without a condition must count its own signals");
    }

    final void complete() {
        fut.complete(null);
    }

    public final CompletableFuture<Void> getFut() {
        return fut;
    }

    public final void join() {
        fut.join();
    }
}
