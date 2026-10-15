package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;

public abstract class CondTask implements Runnable {
    protected volatile boolean done = false;
    protected final CompletableFuture<Void> fut = new CompletableFuture<>();

    public abstract void run();
    
    public final boolean isDone() { return done; }

    /**
     * Marks this task as done because it threw, and completes its future exceptionally. Never throws.
     *
     * @param t The exception thrown by the task.
     */
    protected final void fail(Throwable t) {
        done = true;
        Pool.reportTaskFailure(t);
        fut.completeExceptionally(t);
    }

    /**
     * Runs the task, failing it if run() throws. Never throws.
     */
    final void runCapturing() {
        try {
            run();
        } catch (Throwable t) {
            fail(t);
        }
    }
}
