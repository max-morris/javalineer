package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The release of a multi-guard acquisition must not wait for the work queued behind one of its guards (claims
 * review F5 (b)). A self-perpetuating queue runs on one guard of the acquisition: each task on it enqueues another
 * on the same guard before it returns, so every task terminates but the queue never empties. The acquisition's own
 * body is made to run while a task of that queue is linked behind it. When the body finishes, every one of its other
 * guards must still be released. Before the fix, the release ran the queue inline on the body's thread, ahead of the
 * release of the last guard, which then stayed held for as long as the queue went on.
 * <p>
 * Each case waits at most TIMEOUT_SECONDS for the guards; on a timeout it stops the queue, so the program never
 * hangs, and exits with status 1.
 */
public class TestInlineHandoff {
    static final long TIMEOUT_SECONDS = 5;

    /** A queue on one guard in which every task enqueues the next one, until stopped. */
    static class Chain {
        final Guard g;
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicInteger links = new AtomicInteger();
        final CountDownLatch stopped = new CountDownLatch(1);
        /** Runs once, inside the first task of the chain (which holds g). */
        final Runnable first;

        Chain(Guard g, Runnable first) {
            this.g = g;
            this.first = first;
        }

        void link() {
            if (links.getAndIncrement() == 0) {
                first.run();
            }
            if (stop.get()) {
                stopped.countDown();
            } else {
                g.runGuarded(this::link);
            }
        }

        void start() {
            // From a pool thread: run inline on the main thread, the uncontended first task would never return.
            Pool.run(() -> g.runGuarded(this::link));
        }

        void stop() throws InterruptedException {
            stop.set(true);
            if (!stopped.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("FAILED: the chain on " + g + " did not stop");
            }
        }
    }

    /**
     * Runs an acquisition of all of guards whose first node queues behind the first task of a chain on
     * guards[chainAt], then checks that every guard other than the chain's is released.
     */
    static boolean check(String name, Guard[] guards, int chainAt) throws Exception {
        var bodyRan = new CountDownLatch(1);
        var chain = new Chain(guards[chainAt], () -> {
            // Inside the chain's first task, which holds guards[chainAt]: the acquisition's node on that guard
            // queues behind this task, and the chain's next task (enqueued just after this) queues behind it.
            Guard.runGuarded(bodyRan::countDown, guards);
        });
        chain.start();
        boolean ok = true;
        try {
            if (!bodyRan.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("FAILED " + name + ": the acquisition's body never ran");
            }
            for (int i = 0; i < guards.length; i++) {
                if (i == chainAt) {
                    continue;
                }
                var probe = guards[i].runGuarded(() -> { });
                try {
                    probe.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    System.out.println("FAILED " + name + ": guard " + i + " still held " + TIMEOUT_SECONDS
                            + " s after the body finished (chain at " + chain.links.get() + " tasks)");
                    ok = false;
                }
            }
        } finally {
            chain.stop();
        }
        if (ok) {
            System.out.println("ok " + name);
        }
        return ok;
    }

    static Guard[] guards(int n) {
        var gs = new Guard[n];
        for (int i = 0; i < n; i++) {
            gs[i] = new Guard();
        }
        // Guard ids ascend in creation order, so gs is in GuardSet order.
        assert GuardSet.of(gs).get(0) == gs[0];
        return gs;
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();

        boolean ok = true;
        for (int round = 0; round < 3 && ok; round++) {
            ok &= check("two guards, chain on the first", guards(2), 0);
            ok &= check("three guards, chain on the second", guards(3), 1);
            ok &= check("three guards, chain on the first", guards(3), 0);
            ok &= check("four guards, chain on the third", guards(4), 2);
        }

        if (!ok) {
            System.out.println("TestInlineHandoff: FAILED");
            System.exit(1);
        }
        System.out.println("TestInlineHandoff: passed");
        System.exit(0);
    }
}
