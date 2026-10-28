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
 * The nested cases check the same for the task which called runGuarded, when the acquisition ran inline inside it:
 * an acquisition of two guards (the trampoline), and a single-guard acquisition (Nest1: its release must not run
 * the queue behind it inside the caller's body either).
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

    /**
     * The caller of an acquisition which runs inline must not be held by the queue behind it either. A task on the
     * outer guards (which holds them) calls runGuarded(body, inner) while the inner guards are free, so the
     * acquisition and its body run inline inside the task; the body links the first task of a chain on inner[0]
     * behind the acquisition. When the body finishes, the inner guards are released, and the chain's next task is
     * owed a run by this thread. Running it here (on master, or a trampoline started inline, or, for a single inner
     * guard, the free_ loop before the Nest1 fix) runs the chain on the task's thread for as long as it goes on, so
     * the task never returns and its outer guards stay held. With several outer levels, each runs the next one in
     * inline inside its body (outer[0] directly around the inner acquisition). Every guard but inner[0] is probed.
     */
    static boolean checkNested(String name, Guard[] inner, Guard[]... outer) throws Exception {
        Guard a = inner[0];
        var chain = new Chain(a, () -> { });
        var bodyRan = new CountDownLatch(1);
        var linked = new CountDownLatch(1);
        Runnable task = () -> Guard.runGuarded(() -> {
            // Inside the inner acquisition, inside the task on the outer guards: the chain's first task queues
            // behind it on a.
            Pool.run(() -> {
                a.runGuarded(chain::link);
                linked.countDown();
            });
            try {
                if (!linked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("FAILED " + name + ": the chain was not linked");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            bodyRan.countDown();
        }, inner);
        for (Guard[] level : outer) {
            final Runnable in = task;
            task = () -> Guard.runGuarded(in, level);
        }
        Pool.run(task);
        boolean ok = true;
        try {
            if (!bodyRan.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("FAILED " + name + ": the acquisition's body never ran");
            }
            var probed = new java.util.ArrayList<Guard>();
            for (int i = 1; i < inner.length; i++) {
                probed.add(inner[i]);
            }
            for (Guard[] level : outer) {
                probed.addAll(java.util.Arrays.asList(level));
            }
            for (Guard g : probed) {
                var probe = g.runGuarded(() -> { });
                try {
                    probe.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    System.out.println("FAILED " + name + ": guard " + g + " still held " + TIMEOUT_SECONDS
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

    /** checkNested on fresh guards, with the given numbers of inner and outer guards (one guard set per level). */
    static boolean checkNested(String name, int inner, int... outer) throws Exception {
        int total = inner;
        for (int k : outer) {
            total += k;
        }
        var gs = guards(total);
        var levels = new Guard[outer.length][];
        int at = inner;
        for (int l = 0; l < outer.length; l++) {
            levels[l] = java.util.Arrays.copyOfRange(gs, at, at + outer[l]);
            at += outer[l];
        }
        return checkNested(name, java.util.Arrays.copyOfRange(gs, 0, inner), levels);
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
            ok &= check("two guards, chain on the second", guards(2), 1);
            ok &= checkNested("an inline acquisition of two guards inside a task on a third, chain on the first",
                    2, 1);
            ok &= checkNested("Nest1: an inline single-guard acquisition inside a task on another guard", 1, 1);
            ok &= checkNested("Nest1: an inline single-guard acquisition inside a task on two other guards", 1, 2);
            ok &= checkNested("Nest1: an inline single-guard acquisition inside an inline single-guard"
                    + " acquisition inside a task on a third guard", 1, 1, 1);
        }

        if (!ok) {
            System.out.println("TestInlineHandoff: FAILED");
            System.exit(1);
        }
        System.out.println("TestInlineHandoff: passed");
        System.exit(0);
    }
}
