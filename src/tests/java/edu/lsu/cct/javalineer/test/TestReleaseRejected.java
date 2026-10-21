package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool that rejects submissions during the release of a multi-guard acquisition must not strand any of the
 * guards: the release falls back to freeing inline.
 */
public class TestReleaseRejected {
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

    static void checkFree(GuardVar<Integer> gv, String what) throws Exception {
        var ran = new AtomicBoolean();
        Guard.runGuarded(gv, v -> ran.set(true)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ran.get(), what);
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();

        for (int k = 0; k <= 4; k++) {
            var a = new GuardVar<>(0);
            var b = new GuardVar<>(0);
            var c = new GuardVar<>(0);
            var gs = GuardSet.of(a, b, c);
            var ran = new AtomicBoolean();

            var rejecting = new RejectAfter(k);
            Pool.setPool(rejecting, false);
            try {
                var f = Guard.runGuarded(gs, () -> ran.set(true));
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                // Back to a working pool for the verification below.
                Pool.setPool(Executors.newCachedThreadPool(), false);
            }
            check(ran.get(), "body ran (k=" + k + ")");
            checkFree(a, "first guard free (k=" + k + ")");
            checkFree(b, "second guard free (k=" + k + ")");
            checkFree(c, "third guard free (k=" + k + ")");
            rejecting.real.shutdown();
        }

        System.out.println("TestReleaseRejected: passed");
        System.exit(0);
    }
}
