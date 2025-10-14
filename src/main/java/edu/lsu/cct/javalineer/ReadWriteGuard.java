package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck0;
import edu.lsu.cct.javalineer.functionalinterfaces.CondTask0;

import java.util.concurrent.CompletableFuture;

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

    boolean runRead(final Runnable r,boolean incrWaiting) {
        if (incrWaiting) {
            readWaiting++;
        }
        if (writersTurn()) {
            return false;
        } else {
            readWaiting--;
            readCount++;
            CompletableFuture<CondTask0> future = new CompletableFuture<>();
            Pool.run(() -> {
                runme(r);
            }).thenRun(() -> {
                Guard.runGuarded(g, () -> {
                    if (--readCount == 0) {
                        cond.signalAll();
                    }
                });
            });
            return true;
        }
    }

    boolean runWrite(final Runnable r, boolean incrWaiting) {
        if (incrWaiting) {
            writeWaiting++;
        }
        if (readCount > 0 || !writersTurn()) {
            return false;
        } else {
            runme(r);
            --writeWaiting;
            cond.signalAll();
            return true;
        }
    }

    void runme(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void runReadGuarded(final Runnable r) {
        Guard.runCondition(cond, new CondCheck0() {
            public boolean incrWaiting = true;
            public boolean check() {
                boolean iw = incrWaiting;
                incrWaiting = false;
                return runRead(r, iw);
            }
        });
    }

    public void runWriteGuarded(final Runnable r) {
        Guard.runCondition(cond, new CondCheck0() {
            boolean incrWaiting = true;
            public boolean check() {
                boolean b = incrWaiting;
                incrWaiting = false;
                return runWrite(r, b);
            }
        });
    }

}
