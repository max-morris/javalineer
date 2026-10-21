package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.ReadWriteGuard;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool that rejects a reader submission must not leave readCount incremented: afterward readers still run
 * (inline) and a writer must still be able to run.
 */
public class TestReadRejected {
    static final long TIMEOUT_SECONDS = 10;

    /** Accepts the first k submissions and rejects every later one. */
    static class RejectAfter implements Executor {
        final ExecutorService real = Executors.newFixedThreadPool(4);
        final AtomicInteger count = new AtomicInteger();
        final int k;

        RejectAfter(int k) {
            this.k = k;
        }

        public void execute(Runnable r) {
            if (count.getAndIncrement() >= k) {
                throw new RejectedExecutionException("rejected submission " + count.get());
            }
            real.execute(r);
        }
    }

    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();

        for (int k = 0; k <= 4; k++) {
            var rw = new ReadWriteGuard();
            var readRan = new AtomicInteger();
            var writeRan = new AtomicBoolean();

            var rejecting = new RejectAfter(k);
            Pool.setPool(rejecting, false);
            try {
                for (int i = 0; i < 3; i++) {
                    rw.runReadGuarded(readRan::incrementAndGet).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } finally {
                // Back to a working pool for the verification below.
                Pool.setPool(Executors.newCachedThreadPool(), false);
            }
            check(readRan.get() == 3, "all readers ran (k=" + k + ")");
            rw.runWriteGuarded(() -> writeRan.set(true)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(writeRan.get(), "writer ran after rejected readers (k=" + k + ")");
            rejecting.real.shutdown();
        }

        System.out.println("TestReadRejected: passed");
        System.exit(0);
    }
}
