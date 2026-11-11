package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.Pool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * The admission policy of a list's ranges (RangeAccountant): reader preference by default, fair admission with
 * -DJAVALINEER_FAIR_RANGES=true. Every case runs under both and checks what its policy promises.
 * <ul>
 * <li>0, a read made while a write is queued: under fair admission it goes behind the write, under reader preference
 * it is admitted at once;</li>
 * <li>1, requests that do not conflict never delay each other: with a write queued on [0, 10), a read and a write on
 * [20, 30) are admitted at once under both policies; a read on [5, 15) goes behind the write only under fair
 * admission;</li>
 * <li>2, a chain of reads, each made inside the body of the one before, while a write is queued: under reader
 * preference the write is admitted after the whole chain, under fair admission after none of it;</li>
 * <li>3, the starvation demo: 16 strands each make 500 reads one after another, and one write is made as they start.
 * Under fair admission the write is admitted after at most a bounded number of reads (those already held or made when
 * the write was made); under reader preference the number is printed, not checked, since it depends on timing;</li>
 * <li>4, a call that reads one list twice, with writes of that list made all along, completes (under fair admission
 * the list is admitted once for the call, or a write between the two admissions would deadlock it);</li>
 * <li>5, random calls on one to three of three lists, with random kinds and ranges, from four threads: every call
 * completes, and no call runs alongside a conflicting one (guard order and exclusion under both policies).</li>
 * </ul>
 */
public class TestFairAdmission {
    static final long TIMEOUT_SECONDS = 120;
    static final long SETTLE_MILLIS = 300;
    static int checks = 0;
    static final boolean FAIR = fairPolicy();

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED (" + (FAIR ? "fair" : "reader preference") + "): " + what);
        }
    }

    // RangeAccountant.FAIR, the policy this JVM runs under, through reflection: the class is package-private.
    static boolean fairPolicy() {
        try {
            Field f = Class.forName("edu.lsu.cct.javalineer.RangeAccountant").getDeclaredField("FAIR");
            f.setAccessible(true);
            return f.getBoolean(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static PartitionRangeProvider range(int begin, int end) {
        return new PartitionRangeProvider() {
            public int numPartitions() {
                return 1;
            }

            public int getWritableBegin(int p) {
                return begin;
            }

            public int getWritableEnd(int p) {
                return end;
            }
        };
    }

    // A read range on all of list that is kept until the returned future is completed: reducePartitioned keeps its
    // read range until its parts' results are in. The second future completes when the range is held.
    static CompletableFuture<Integer> keepRead(PartitionableList<Integer> list, CompletableFuture<Void> held) {
        var result = new CompletableFuture<Integer>();
        var first = new AtomicBoolean(true);
        list.reducePartitioned(1, view -> {
            if (first.getAndSet(false)) {
                held.complete(null);
                return result;
            }
            return CompletableFuture.completedFuture(0);
        });
        return result;
    }

    static void readBehindQueuedWrite() throws Exception {
        var a = PartitionableList.of(100, i -> i);
        var order = Collections.synchronizedList(new ArrayList<String>());
        var held = new CompletableFuture<Void>();
        var kept = keepRead(a, held);
        held.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var w = PartitionableList.runPartitioned(1, a.write(), v -> order.add("W"));
        Thread.sleep(SETTLE_MILLIS);
        check(!w.isDone(), "the write waits for the kept read range");
        var r = PartitionableList.runPartitioned(1, a.read(), v -> order.add("R"));
        if (FAIR) {
            Thread.sleep(SETTLE_MILLIS);
            check(!r.isDone(), "the read waits behind the queued write");
        } else {
            r.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(!w.isDone(), "the read passed the queued write, which still waits");
        }
        kept.complete(0);
        CompletableFuture.allOf(w, r).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(order.equals(FAIR ? List.of("W", "R") : List.of("R", "W")), "admission order, got " + order);
    }

    static void disjointNotDelayed() throws Exception {
        var a = PartitionableList.of(40, i -> i);
        var running = new CountDownLatch(1);
        var letGo = new CountDownLatch(1);
        var order = Collections.synchronizedList(new ArrayList<String>());
        // A read of [0, 10) held by a body that waits (a plain thread makes the call, so that the body, run inline on
        // the calling thread, blocks only that thread)
        var holder = new CompletableFuture<Void>();
        var t = new Thread(() -> PartitionableList.runPartitioned(range(0, 10), a.read(), v -> {
            running.countDown();
            try {
                letGo.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((v, e) -> holder.complete(null)));
        t.setDaemon(true);
        t.start();
        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the read of [0, 10) is held");
        var w = PartitionableList.runPartitioned(range(0, 10), a.write(), v -> order.add("W0-10"));
        Thread.sleep(SETTLE_MILLIS);
        check(!w.isDone(), "the write of [0, 10) waits for the held read");
        // Disjoint from the queued write: admitted at once under both policies
        PartitionableList.runPartitioned(range(20, 30), a.read(), v -> order.add("R20-30"))
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableList.runPartitioned(range(20, 30), a.write(), v -> order.add("W20-30"))
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // Overlaps the queued write, not the held read
        var r = PartitionableList.runPartitioned(range(5, 15), a.read(), v -> order.add("R5-15"));
        if (FAIR) {
            Thread.sleep(SETTLE_MILLIS);
            check(!r.isDone(), "the read of [5, 15) waits behind the queued write of [0, 10)");
        } else {
            r.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        // A read of [12, 14) overlaps neither the queued write nor the held read: admitted at once under both
        PartitionableList.runPartitioned(range(12, 14), a.read(), v -> order.add("R12-14"))
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        letGo.countDown();
        CompletableFuture.allOf(holder, w, r).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var expected = FAIR ? List.of("R20-30", "W20-30", "R12-14", "W0-10", "R5-15")
                            : List.of("R20-30", "W20-30", "R5-15", "R12-14", "W0-10");
        check(order.equals(expected), "admission order " + expected + ", got " + order);
    }

    static final int CHAIN = 200;

    // Read i of the chain: its body makes read i + 1, until CHAIN reads have run after the first. Each read counts
    // down done when its call is over.
    static void chainRead(PartitionableList<Integer> a, int i, AtomicBoolean writeRan, AtomicInteger readsRun,
                          AtomicInteger passed, CountDownLatch started, CountDownLatch go, CountDownLatch done) {
        PartitionableList.runPartitioned(1, a.read(), v -> {
            if (i == 0) {
                started.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                readsRun.incrementAndGet();
                if (!writeRan.get()) {
                    passed.incrementAndGet();
                }
            }
            if (i < CHAIN) {
                chainRead(a, i + 1, writeRan, readsRun, passed, started, go, done);
            }
        }).whenComplete((v, e) -> done.countDown());
    }

    static void batonChain() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var readsRun = new AtomicInteger();
        var writeRan = new AtomicBoolean();
        var passed = new AtomicInteger();
        var started = new CountDownLatch(1);
        var go = new CountDownLatch(1);
        var done = new CountDownLatch(CHAIN + 1);
        // A big stack: under reader preference each read of the chain runs inline inside the body of the one before
        var t = new Thread(null, () -> chainRead(a, 0, writeRan, readsRun, passed, started, go, done), "chain",
                           256L << 20);
        t.setDaemon(true);
        t.start();
        check(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the first read is held");
        var w = PartitionableList.runPartitioned(1, a.write(), v -> writeRan.set(true));
        Thread.sleep(SETTLE_MILLIS);
        check(!w.isDone(), "the write waits for the first read");
        go.countDown();
        w.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the whole chain is over");
        check(readsRun.get() == CHAIN, "the whole chain ran, got " + readsRun.get());
        System.out.println("  baton: the write was admitted after " + passed.get() + " of " + CHAIN + " reads");
        check(passed.get() == (FAIR ? 0 : CHAIN), "reads before the write, got " + passed.get());
    }

    static final int STRANDS = 16, READS = 500;

    static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    // The strand's read r: its body spins, and its completion makes read r + 1.
    static void strandRead(PartitionableList<Integer> a, int r, AtomicInteger begun, AtomicBoolean writing,
                           AtomicInteger overlaps, CountDownLatch strandsDone) {
        if (r == READS) {
            strandsDone.countDown();
            return;
        }
        PartitionableList.runPartitioned(1, a.read(), v -> {
            begun.incrementAndGet();
            if (writing.get()) {
                overlaps.incrementAndGet();
            }
            spin(20_000);
            if (writing.get()) {
                overlaps.incrementAndGet();
            }
        }).whenComplete((v, e) -> Pool.getPool().execute(
                () -> strandRead(a, r + 1, begun, writing, overlaps, strandsDone)));
    }

    static int starvationRun() throws Exception {
        var a = PartitionableList.of(64, i -> i);
        var begun = new AtomicInteger();
        var writing = new AtomicBoolean();
        var overlaps = new AtomicInteger();
        var strandsDone = new CountDownLatch(STRANDS);
        var atWrite = new AtomicInteger(-1);
        // Each strand starts in the pool, and makes its next read from a new pool task (not inline in the release of
        // the read before, which would run the strand on one stack); the write is made as the strands get going.
        for (int s = 0; s < STRANDS; s++) {
            Pool.getPool().execute(() -> strandRead(a, 0, begun, writing, overlaps, strandsDone));
        }
        while (begun.get() < STRANDS) {
            Thread.onSpinWait();
        }
        int atIssue = begun.get();
        var w = PartitionableList.runPartitioned(1, a.write(), v -> {
            writing.set(true);
            atWrite.set(begun.get());
            spin(20_000);
            writing.set(false);
        });
        w.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(strandsDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "every strand finishes its reads");
        check(overlaps.get() == 0, "no read ran alongside the write, got " + overlaps.get());
        check(begun.get() == STRANDS * READS, "every read ran, got " + begun.get());
        return atWrite.get() - atIssue;
    }

    static void starvation() throws Exception {
        int runs = 5;
        var counts = new ArrayList<Integer>();
        for (int i = 0; i < runs; i++) {
            int passed = starvationRun();
            counts.add(passed);
            if (FAIR) {
                // Held or made before the write's first check: at most one read per strand for each (a strand makes
                // its next read only when the one before is over), so 2 * STRANDS; anything later goes behind it.
                check(passed <= 2 * STRANDS, "the write was admitted after " + passed + " reads made after it, more "
                        + "than " + (2 * STRANDS));
            }
        }
        System.out.println("  starvation: reads begun after the write was made and before it ran, in " + runs
                + " runs of " + STRANDS + " x " + READS + ": " + counts);
    }

    static void sameListTwice() throws Exception {
        var a = PartitionableList.of(32, i -> i);
        var b = PartitionableList.of(32, i -> 0);
        int rounds = 2000;
        var futs = new ArrayList<CompletableFuture<Void>>();
        var start = new CountDownLatch(1);
        var issued = new CountDownLatch(2);
        for (int side = 0; side < 2; side++) {
            final boolean reader = side == 0;
            var t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                var mine = new ArrayList<CompletableFuture<Void>>();
                for (int r = 0; r < rounds; r++) {
                    if (reader) {
                        mine.add(PartitionableList.runPartitioned(2, a.read(), a.read(), b.write(), (x, y, z) -> {
                            for (int i = 0; i < z.writableSize(); i++) {
                                z.set(i, x.get(i) + y.get(i));
                            }
                        }));
                    } else {
                        mine.add(PartitionableList.runPartitioned(2, a.readWrite(), v -> {
                            for (int i = 0; i < v.writableSize(); i++) {
                                v.set(i, v.get(i) + 1);
                            }
                        }));
                    }
                }
                synchronized (futs) {
                    futs.addAll(mine);
                }
                issued.countDown();
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        check(issued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "both sides make their calls");
        CompletableFuture.allOf(futs.toArray(CompletableFuture[]::new)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "nothing held after the calls");
    }

    static final int CELLS = 48;

    // Per cell of each list, the calls inside their bodies that read it and that write it.
    static final class Marks {
        final AtomicIntegerArray readers = new AtomicIntegerArray(CELLS);
        final AtomicIntegerArray writers = new AtomicIntegerArray(CELLS);
    }

    static void enter(Marks m, boolean reads, boolean writes, int lo, int hi, AtomicInteger faults) {
        for (int i = lo; i < hi; i++) {
            if (writes) {
                if (m.writers.incrementAndGet(i) != 1 || m.readers.get(i) != 0) {
                    faults.incrementAndGet();
                }
            } else if (reads) {
                m.readers.incrementAndGet(i);
                if (m.writers.get(i) != 0) {
                    faults.incrementAndGet();
                }
            }
        }
    }

    static void leave(Marks m, boolean reads, boolean writes, int lo, int hi) {
        for (int i = lo; i < hi; i++) {
            if (writes) {
                m.writers.decrementAndGet(i);
            } else if (reads) {
                m.readers.decrementAndGet(i);
            }
        }
    }

    static void randomMix() throws Exception {
        @SuppressWarnings("unchecked")
        PartitionableList<Integer>[] lists = new PartitionableList[] {
                PartitionableList.of(CELLS, i -> i), PartitionableList.of(CELLS, i -> i), PartitionableList.of(CELLS, i -> i)};
        var marks = new Marks[] {new Marks(), new Marks(), new Marks()};
        var faults = new AtomicInteger();
        int threads = 4, calls = 1500;
        var futs = Collections.synchronizedList(new ArrayList<CompletableFuture<Void>>());
        var start = new CountDownLatch(1);
        var issued = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            var th = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                var rnd = ThreadLocalRandom.current();
                for (int c = 0; c < calls; c++) {
                    int lo = rnd.nextInt(CELLS), hi = lo + 1 + rnd.nextInt(Math.min(12, CELLS - lo));
                    var range = range(lo, hi);
                    int x = rnd.nextInt(3), y = (x + 1 + rnd.nextInt(2)) % 3;
                    // 0: read, 1: write, 2: read and write
                    int kx = rnd.nextInt(3), ky = rnd.nextInt(3);
                    boolean two = rnd.nextBoolean();
                    final int fx = x, fy = y;
                    if (!two) {
                        futs.add(callOne(lists[x], kx, range, () -> {
                            enter(marks[fx], kx != 1, kx != 0, lo, hi, faults);
                            spin(2_000);
                            leave(marks[fx], kx != 1, kx != 0, lo, hi);
                        }));
                    } else {
                        futs.add(callTwo(lists[x], kx, lists[y], ky, range, () -> {
                            enter(marks[fx], kx != 1, kx != 0, lo, hi, faults);
                            enter(marks[fy], ky != 1, ky != 0, lo, hi, faults);
                            spin(2_000);
                            leave(marks[fy], ky != 1, ky != 0, lo, hi);
                            leave(marks[fx], kx != 1, kx != 0, lo, hi);
                        }));
                    }
                }
                issued.countDown();
            });
            th.setDaemon(true);
            th.start();
        }
        start.countDown();
        check(issued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "every thread makes its calls");
        synchronized (futs) {
            CompletableFuture.allOf(futs.toArray(CompletableFuture[]::new)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        check(faults.get() == 0, "no call ran alongside a conflicting one, got " + faults.get());
        for (var l : lists) {
            check(TestPartitionedRelease.heldRanges(l) == 0, "nothing held after the calls");
        }
    }

    static CompletableFuture<Void> callOne(PartitionableList<Integer> l, int k, PartitionRangeProvider range,
                                           Runnable body) {
        switch (k) {
            case 0:
                return PartitionableList.runPartitioned(range, l.read(), v -> body.run());
            case 1:
                return PartitionableList.runPartitioned(range, l.write(), v -> body.run());
            default:
                return PartitionableList.runPartitioned(range, l.readWrite(), v -> body.run());
        }
    }

    static CompletableFuture<Void> callTwo(PartitionableList<Integer> l1, int k1, PartitionableList<Integer> l2, int k2,
                                           PartitionRangeProvider range, Runnable body) {
        switch (k1 * 3 + k2) {
            case 0:
                return PartitionableList.runPartitioned(range, l1.read(), l2.read(), (u, v) -> body.run());
            case 1:
                return PartitionableList.runPartitioned(range, l1.read(), l2.write(), (u, v) -> body.run());
            case 2:
                return PartitionableList.runPartitioned(range, l1.read(), l2.readWrite(), (u, v) -> body.run());
            case 3:
                return PartitionableList.runPartitioned(range, l1.write(), l2.read(), (u, v) -> body.run());
            case 4:
                return PartitionableList.runPartitioned(range, l1.write(), l2.write(), (u, v) -> body.run());
            case 5:
                return PartitionableList.runPartitioned(range, l1.write(), l2.readWrite(), (u, v) -> body.run());
            case 6:
                return PartitionableList.runPartitioned(range, l1.readWrite(), l2.read(), (u, v) -> body.run());
            case 7:
                return PartitionableList.runPartitioned(range, l1.readWrite(), l2.write(), (u, v) -> body.run());
            default:
                return PartitionableList.runPartitioned(range, l1.readWrite(), l2.readWrite(), (u, v) -> body.run());
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        System.out.println("TestFairAdmission under " + (FAIR ? "fair admission" : "reader preference"));
        // An argument (0 to 5) runs just that case
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        if (only < 0 || only == 0) readBehindQueuedWrite();
        if (only < 0 || only == 1) disjointNotDelayed();
        if (only < 0 || only == 2) batonChain();
        if (only < 0 || only == 3) starvation();
        if (only < 0 || only == 4) sameListTwice();
        if (only < 0 || only == 5) randomMix();
        System.out.println("TestFairAdmission passed (" + checks + " checks)");
        System.exit(0);
    }
}
