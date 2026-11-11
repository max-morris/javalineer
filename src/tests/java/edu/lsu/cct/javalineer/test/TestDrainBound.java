package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded drains (J1, Pool.setDrainBound). A drain runs at most B tasks inline and hands the rest back to the pool,
 * so that a pool worker returns regularly even while a guard stays hot forever, and a submission waiting in the pool
 * is reached.
 * <p>
 * The starvation cases use a pool of ONE worker and a guard that never goes quiet:
 * <ul>
 * <li><b>chain</b>: a self-perpetuating queue (every task enqueues the next on the same guard), started from the
 * worker, so the worker's top-level drain never finds the queue empty;</li>
 * <li><b>feeder</b>: an outside (non-pool) thread keeps a backlog of about 100,000 tasks on the guard, whose drain runs on
 * the worker; the backlog is deep enough to outlast a preemption of the feeder;</li>
 * </ul>
 * Then a marker task is submitted to the pool. With bounded drains on, the marker must run within MARKER_SECONDS; with
 * them off (B = 0, the behavior before J1) it must not, which shows that the test is not vacuous (skipped when the
 * process runs with -DJAVALINEER_DRAIN_BOUND set, or with the "on-only" argument).
 * <p>
 * The stress case checks safety with tiny bounds (B = 1, 2, 3): mutual exclusion per guard and per-issuer order on
 * single- and multi-guard acquisitions issued from pool tasks and from inside bodies (order checked on each
 * acquisition's lowest guard, where it enqueues at issue time), and that every task ran.
 * Exits with status 1 on any failure; never hangs (every chain and feeder is stopped on the way out).
 */
public class TestDrainBound {
    static final long MARKER_SECONDS = 3;
    /** The feeder's backlog: deep enough that a preempted feeder does not let the queue empty (about 25 ms of work). */
    static final int BACKLOG = 100_000;
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok: " + what);
        }
    }

    /** Spins for about ns nanoseconds. */
    static void spin(long ns) {
        long end = System.nanoTime() + ns;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    /** Case chain: returns whether the marker ran within the limit. */
    static boolean chain(int bound) throws Exception {
        Pool.setDrainBound(bound);
        Guard g = new Guard();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong links = new AtomicLong();
        CountDownLatch stopped = new CountDownLatch(1);
        Runnable[] link = new Runnable[1];
        link[0] = () -> {
            links.incrementAndGet();
            spin(200);
            if (stop.get()) {
                stopped.countDown();
            } else {
                g.runGuarded(link[0]);
            }
        };
        Pool.run(() -> g.runGuarded(link[0]));
        while (links.get() < 1_000) {
            Thread.onSpinWait();
        }
        CountDownLatch marker = new CountDownLatch(1);
        Pool.run(marker::countDown);
        boolean ran = marker.await(MARKER_SECONDS, TimeUnit.SECONDS);
        stop.set(true);
        boolean drained = stopped.await(30, TimeUnit.SECONDS);
        marker.await(30, TimeUnit.SECONDS);
        check(drained, "chain B=" + bound + ": the chain stopped (" + links.get() + " links)");
        return ran;
    }

    /** Case feeder: returns whether the marker ran within the limit. */
    static boolean feeder(int bound) throws Exception {
        Pool.setDrainBound(bound);
        Guard g = new Guard();
        AtomicLong issued = new AtomicLong(), done = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean();
        Runnable body = () -> {
            spin(1_000);
            done.incrementAndGet();
        };
        // The first task goes through the worker, so that the drain runs there.
        CountDownLatch started = new CountDownLatch(1);
        issued.incrementAndGet();
        Pool.run(() -> g.runGuarded(() -> {
            // Hold the guard until the feeder has queued a backlog behind this task, so that the drain starts on
            // the worker (a feeder that found the guard free would run its own tasks inline instead).
            started.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (issued.get() < BACKLOG / 2 && System.nanoTime() < until) {
                Thread.onSpinWait();
            }
            done.incrementAndGet();
        }));
        started.await();
        Thread feed = new Thread(() -> {
            while (!stop.get()) {
                if (issued.get() - done.get() < BACKLOG) {
                    issued.incrementAndGet();
                    g.runGuarded(body);
                } else {
                    Thread.onSpinWait();
                }
            }
        });
        feed.start();
        long until0 = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((done.get() < 2_000 || issued.get() - done.get() < BACKLOG / 2) && System.nanoTime() < until0) {
            Thread.onSpinWait();
        }
        CountDownLatch marker = new CountDownLatch(1);
        String[] who = new String[1];
        long backlog0 = issued.get() - done.get();
        Pool.run(() -> { who[0] = Thread.currentThread().getName() + " backlog " + (issued.get() - done.get()); marker.countDown(); });
        boolean ran = marker.await(MARKER_SECONDS, TimeUnit.SECONDS);
        System.out.println("  feeder B=" + bound + ": backlog at submission " + backlog0 + ", marker " + (ran ? "ran on " + who[0] : "did not run"));
        stop.set(true);
        feed.join();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (done.get() < issued.get() && System.nanoTime() < until) {
            Thread.sleep(1);
        }
        marker.await(30, TimeUnit.SECONDS);
        check(done.get() == issued.get(), "feeder B=" + bound + ": every task ran (" + done.get() + " of " + issued.get() + ")");
        return ran;
    }

    /** Safety with tiny bounds on the configured pool. */
    static void stress(int bound) throws Exception {
        Pool.setDrainBound(bound);
        final int nG = 3, issuers = 6, ops = 20_000;
        Guard[] gs = new Guard[nG];
        AtomicInteger[] inside = new AtomicInteger[nG];
        for (int i = 0; i < nG; i++) {
            gs[i] = new Guard();
            inside[i] = new AtomicInteger();
        }
        GuardSet[] sets = {GuardSet.of(gs[0]), GuardSet.of(gs[1]), GuardSet.of(gs[2]), GuardSet.of(gs[0], gs[1]),
                GuardSet.of(gs[1], gs[2]), GuardSet.of(gs[0], gs[1], gs[2])};
        int[][] members = {{0}, {1}, {2}, {0, 1}, {1, 2}, {0, 1, 2}};
        long[][] seen = new long[issuers][nG];
        AtomicLong ran = new AtomicLong();
        AtomicInteger bad = new AtomicInteger();
        CountDownLatch all = new CountDownLatch(issuers * ops);
        for (int p = 0; p < issuers; p++) {
            final int id = p;
            Pool.run(() -> {
                long[] issuedOn = new long[nG];
                for (int i = 0; i < ops; i++) {
                    int k = (i * 7 + id) % sets.length;
                    // Per-issuer order holds on an acquisition's lowest guard, where it enqueues at issue time.
                    final int low = members[k][0];
                    final long need = issuedOn[low]++;
                    final int kk = k;
                    Guard.runGuarded(sets[k], () -> {
                        for (int x : members[kk]) {
                            if (inside[x].incrementAndGet() != 1) bad.incrementAndGet();
                        }
                        // per-issuer order: this issuer's earlier operations with the same lowest guard ran first
                        if (seen[id][low] != need) bad.incrementAndGet();
                        seen[id][low]++;
                        if (kk == 0 && id % 2 == 0) {
                            // nested acquisition from inside a body: its successor goes to the pool (Nest1)
                            Guard.runGuarded(sets[2], ran::incrementAndGet).thenRun(() -> {});
                        }
                        for (int x : members[kk]) inside[x].decrementAndGet();
                        all.countDown();
                    });
                }
            });
        }
        boolean finished = all.await(120, TimeUnit.SECONDS);
        check(finished && bad.get() == 0, "stress B=" + bound + ": exclusion and per-issuer order (" + bad.get()
                + " violations, finished " + finished + ")");
    }

    public static void main(String[] args) throws Exception {
        boolean onOnly = System.getProperty("JAVALINEER_DRAIN_BOUND") != null
                || (args.length > 0 && args[0].equals("on-only"));
        int before = Pool.getDrainBound();
        Executor saved = Pool.getPool();
        ExecutorService one = (ExecutorService) Pool.newFixedPool(1);
        Pool.setPool(one, false);
        try {
            check(chain(64), "chain B=64: the marker ran while the chain went on");
            check(feeder(64), "feeder B=64: the marker ran while the feeder went on");
            check(chain(1), "chain B=1: the marker ran");
            if (!onOnly) {
                check(!chain(0), "chain B=0 (off): the marker starved, as before J1");
                check(!feeder(0), "feeder B=0 (off): the marker starved, as before J1");
            }
        } finally {
            Pool.setPool(saved, false);
            one.shutdown();
        }
        for (int b : new int[]{1, 2, 3, 0}) {
            stress(b);
        }
        Pool.setDrainBound(-5);
        check(Pool.getDrainBound() == 0, "a negative bound means off");
        Pool.setDrainBound(before);
        System.out.println(failures == 0 ? "TestDrainBound: PASS" : "TestDrainBound: FAIL " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
