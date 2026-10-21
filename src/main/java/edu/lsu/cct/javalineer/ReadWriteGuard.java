package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck0;
import edu.lsu.cct.javalineer.functionalinterfaces.CondTask0;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * This is a fairly simple guard that supports reading and writing.
 * TODO: Figure out how to run multiple read/write guarded threads at once.
 * @author stevenrbrandt
 */
public class ReadWriteGuard {
    Guard g = new Guard();
    CondContext<CondTask0> cond = Guard.newCondition(g);

    /** An ideal ratio of readers to writers. */
    final int balance;

    public ReadWriteGuard(int balance) {
        this.balance = balance;
    }

    public ReadWriteGuard() {
        this(5);
    }

    /** The number of read threads currently running. */
    int readCount = 0;
    /** The number of write threads waiting to run. */
    int writeWaiting = 0;
    /** The number of read threads waiting to run. */
    int readWaiting = 0;

    /**
     * Enforce some kind of balance between read and write threads based
     * on the ratio of readers waiting to writers waiting.
     * @return
     */
    boolean writersTurn() {
        return writeWaiting * balance > readWaiting;
    }

    boolean runRead(final Runnable r, boolean incrWaiting, CompletableFuture<Void> done) {
        if (incrWaiting) {
            readWaiting++;
        }
        if (writersTurn()) {
            return false;
        } else {
            readWaiting--;
            readCount++;
            CompletableFuture<Void> ran;
            try {
                ran = Pool.run(r);
            } catch (RejectedExecutionException e) {
                // The pool was shut down or swapped. readCount was already incremented, and the completion
                // callback below is what decrements it, so the invariant (readCount == readers that will still
                // run and release) requires that this reader still runs and still releases. Undoing the
                // increment instead would drop the read and need its own signalAll bookkeeping. Running r inline
                // is always correct: it only holds up this check (as a writer already does) and never safety.
                // runCapturing never throws, so the release below always happens.
                ran = new CompletableFuture<>();
                Pool.runCapturing(r, ran);
            }
            ran.whenComplete((v, t) -> {
                // Release the read slot whether or not r threw
                Guard.runGuarded(g, () -> {
                    if (--readCount == 0) {
                        cond.signalAll();
                    }
                });
                if (t != null) {
                    done.completeExceptionally(t);
                } else {
                    done.complete(null);
                }
            });
            return true;
        }
    }

    boolean runWrite(final Runnable r, boolean incrWaiting, CompletableFuture<Void> done) {
        if (incrWaiting) {
            writeWaiting++;
        }
        if (readCount > 0 || !writersTurn()) {
            return false;
        } else {
            // Never throws, so the bookkeeping below always happens
            Pool.runCapturing(r, done);
            --writeWaiting;
            cond.signalAll();
            return true;
        }
    }

    /**
     * Runs r with read access. Multiple readers may run concurrently.
     *
     * @return A future which completes when r returns, or completes exceptionally if r throws.
     */
    public CompletableFuture<Void> runReadGuarded(final Runnable r) {
        var done = new CompletableFuture<Void>();
        Guard.runCondition(cond, new CondCheck0() {
            public boolean incrWaiting = true;
            public boolean check() {
                boolean iw = incrWaiting;
                incrWaiting = false;
                return runRead(r, iw, done);
            }
        }).whenComplete((v, t) -> {
            if (t != null) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    /**
     * Runs r with exclusive write access.
     *
     * @return A future which completes when r returns, or completes exceptionally if r throws.
     */
    public CompletableFuture<Void> runWriteGuarded(final Runnable r) {
        var done = new CompletableFuture<Void>();
        Guard.runCondition(cond, new CondCheck0() {
            boolean incrWaiting = true;
            public boolean check() {
                boolean b = incrWaiting;
                incrWaiting = false;
                return runWrite(r, b, done);
            }
        }).whenComplete((v, t) -> {
            if (t != null) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

}
