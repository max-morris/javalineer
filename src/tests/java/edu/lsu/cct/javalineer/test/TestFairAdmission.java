package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.PartitionableListExt;
import edu.lsu.cct.javalineer.Pool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * completes, and no call runs alongside a conflicting one (guard order and exclusion under both policies);</li>
 * <li>6 to 9, a list given more than once to one call. Under fair admission all of a call's positions on one list are
 * one admission (PartCall), so a write made between two of them cannot deadlock the call (finding R4-1 of Guardian's
 * proof/notes/partadmit.md). In 6, 8 and 9 a held write keeps the call waiting, a write made after the call queues
 * behind it, and the held write is then released: the call must complete within HANG_SECONDS, and each write after
 * it. 6: one list read twice; 7: a list given twice with a write is still rejected at the call, under both policies,
 * and nothing is held after; 8: two reads of one list over overlapping parts (a provider's, and ghosts), with a
 * write of the overlap; 9: two lists each read twice, positions interleaved, with a write of each list and a write of
 * both.</li>
 * </ul>
 */
public class TestFairAdmission {
    static final long TIMEOUT_SECONDS = 120;
    static final long SETTLE_MILLIS = 300;
    // How long a call that a write made between two of its admissions would deadlock is given (cases 6, 8, 9)
    static final long HANG_SECONDS = 20;
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

    // Runs call on a plain thread and returns once its body is running: the body (call's argument) waits for letGo.
    // A one-part call admitted at once runs its body inline on the calling thread, so only that thread blocks. The
    // returned future completes when the call does.
    static CompletableFuture<Void> holdUntil(CountDownLatch letGo,
                                            Function<Runnable, CompletableFuture<Void>> call) throws Exception {
        var running = new CountDownLatch(1);
        var over = new CompletableFuture<Void>();
        Runnable body = () -> {
            running.countDown();
            try {
                letGo.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        var t = new Thread(() -> call.apply(body).whenComplete((v, e) -> {
            if (e != null) {
                over.completeExceptionally(e);
            } else {
                over.complete(null);
            }
        }));
        t.setDaemon(true);
        t.start();
        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the holding call's body runs");
        return over;
    }

    // Waits for a call that would deadlock if a write made between two of its admissions on one list got ahead of
    // the second (finding R4-1).
    static void completesOrHangs(CompletableFuture<Void> f, String what) throws Exception {
        try {
            f.get(HANG_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            check(false, what + " did not complete within " + HANG_SECONDS + " s: a write made between two "
                    + "admissions of one list in one call deadlocks it");
        }
    }

    // The shape of cases 6, 8 and 9: hold makes a call that holds what call needs until it is released; call is
    // made, then the writes, each once the one before is queued; then the hold is released. call must complete, and
    // so must each write. Under fair admission the writes named in after must run after call: they are queued behind
    // it on the first list it waits for. (A write of only a later list of call's may be admitted there first.)
    static void writeBetween(String name, Function<Runnable, CompletableFuture<Void>> hold,
                             Function<List<String>, CompletableFuture<Void>> call,
                             List<Function<List<String>, CompletableFuture<Void>>> writes, List<String> after,
                             PartitionableList<?>... lists) throws Exception {
        var order = Collections.synchronizedList(new ArrayList<String>());
        var letGo = new CountDownLatch(1);
        var held = holdUntil(letGo, hold);
        var c = call.apply(order);
        Thread.sleep(SETTLE_MILLIS);
        check(!c.isDone(), name + ": the call waits for the held write");
        var ws = new ArrayList<CompletableFuture<Void>>();
        for (var w : writes) {
            ws.add(w.apply(order));
            Thread.sleep(SETTLE_MILLIS);
        }
        for (var w : ws) {
            check(!w.isDone(), name + ": a write made after the call waits");
        }
        letGo.countDown();
        completesOrHangs(c, name + ": the call");
        for (var w : ws) {
            completesOrHangs(w, name + ": a write made after the call");
        }
        held.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (FAIR) {
            for (var w : after) {
                check(order.indexOf("call") >= 0 && order.indexOf("call") < order.indexOf(w),
                      name + ": the call ran before " + w + ", got " + order);
            }
        } else {
            System.out.println("  " + name + " (reader preference): " + order);
        }
        check(order.size() == 1 + writes.size(), name + ": every call ran once, got " + order);
        for (var l : lists) {
            check(TestPartitionedRelease.heldRanges(l) == 0, name + ": nothing held after the calls");
        }
    }

    // 6: one list read twice in one call (TwiceRead).
    static void twiceReadWriteBetween() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var writing = new AtomicBoolean();
        var bad = new AtomicInteger();
        writeBetween("twice read",
                     body -> PartitionableList.runPartitioned(1, a.write(), v -> body.run()),
                     order -> PartitionableList.runPartitioned(1, a.read(), a.read(), (x, y) -> {
                         if (writing.get() || !x.get(3).equals(y.get(3))) {
                             bad.incrementAndGet();
                         }
                         order.add("call");
                     }),
                     List.of(order -> PartitionableList.runPartitioned(1, a.write(), v -> {
                         writing.set(true);
                         order.add("W");
                         writing.set(false);
                     })),
                     List.of("W"), a);
        // The same through runPartitionedFut, whose parts return futures
        writeBetween("twice read, runPartitionedFut",
                     body -> PartitionableList.runPartitioned(1, a.write(), v -> body.run()),
                     order -> PartitionableListExt.runPartitionedFut(1, a.read(), a.read(), (x, y) -> {
                         order.add("call");
                         return CompletableFuture.completedFuture(null);
                     }),
                     List.of(order -> PartitionableList.runPartitioned(1, a.write(), v -> order.add("W"))),
                     List.of("W"), a);
        check(bad.get() == 0, "twice read: the call saw no write");
    }

    // 7: a list given twice with a write is rejected at the call under both policies (PartCall's constructor), with
    // nothing admitted; with read intents only, the same calls run (TwinRW, with the check kept).
    static void twinReadWriteRejected() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var b = PartitionableList.of(16, i -> i);
        var ran = new AtomicInteger();
        List<Supplier<CompletableFuture<Void>>> rejected = List.of(
                () -> PartitionableList.runPartitioned(2, a.read(), a.write(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(2, a.write(), a.read(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(2, a.read(), a.readWrite(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(2, a.readWrite(), a.read(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(2, a.write(), a.write(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(2, a.readWrite(), a.readWrite(),
                                                       (x, y) -> ran.incrementAndGet()),
                // With ghosts: a merged read-write admission would let part 0's ghost read meet part 1's write
                () -> PartitionableList.runPartitioned(2, 1, a.read(), a.write(), (x, y) -> ran.incrementAndGet()),
                () -> PartitionableList.runPartitioned(range(0, 8), a.read(), a.write(),
                                                       (x, y) -> ran.incrementAndGet()),
                // Not adjacent as given: the guard-order sort puts them side by side
                () -> PartitionableList.runPartitioned(2, a.read(), b.read(), a.write(),
                                                       (x, y, z) -> ran.incrementAndGet()),
                () -> PartitionableListExt.runPartitionedFut(2, a.read(), a.readWrite(), (x, y) -> {
                    ran.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }));
        int n = 0;
        for (var call : rejected) {
            boolean threw = false;
            try {
                call.get();
            } catch (IllegalArgumentException e) {
                threw = e.getMessage() != null && e.getMessage().contains("more than once");
            }
            check(threw, "twin read-write: call " + n + " is rejected at the call");
            n++;
        }
        check(ran.get() == 0, "twin read-write: no part of a rejected call ran, got " + ran.get());
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "twin read-write: nothing held after the rejected calls");
        PartitionableList.runPartitioned(2, 1, a.read(), a.read(), (x, y) -> ran.incrementAndGet())
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableList.runPartitioned(2, a.read(), b.read(), a.read(), (x, y, z) -> ran.incrementAndGet())
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ran.get() == 4, "twin read-write: the read-only twins ran, got " + ran.get());
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "twin read-write: nothing held after the read-only twins");
    }

    // A provider of two parts, [0, 10) and [6, 16): they overlap, which a call that only reads may do.
    static PartitionRangeProvider overlapping() {
        return new PartitionRangeProvider() {
            public int numPartitions() {
                return 2;
            }

            public int getWritableBegin(int p) {
                return p == 0 ? 0 : 6;
            }

            public int getWritableEnd(int p) {
                return p == 0 ? 10 : 16;
            }
        };
    }

    // 8: two reads of one list over overlapping parts, and a write of the overlap made between (TwiceReadOverlap).
    static void overlapReadsWriteBetween() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        writeBetween("overlapping parts",
                     body -> PartitionableList.runPartitioned(1, a.write(), v -> body.run()),
                     order -> PartitionableList.runPartitioned(overlapping(), a.read(), a.read(), (x, y) -> {
                         synchronized (order) {
                             if (!order.contains("call")) {
                                 order.add("call");
                             }
                         }
                     }),
                     List.of(order -> PartitionableList.runPartitioned(range(6, 10), a.write(), v -> order.add("W"))),
                     List.of("W"), a);
        // Ghosts: two parts of [2, 8) and [8, 14) read [0, 10) and [6, 16); the write is of [6, 10)
        writeBetween("overlapping ghosts",
                     body -> PartitionableList.runPartitioned(1, a.write(), v -> body.run()),
                     order -> PartitionableList.runPartitioned(2, 2, a.read(), a.read(), (x, y) -> {
                         synchronized (order) {
                             if (!order.contains("call")) {
                                 order.add("call");
                             }
                         }
                     }),
                     List.of(order -> PartitionableList.runPartitioned(range(6, 10), a.write(), v -> order.add("W"))),
                     List.of("W"), a);
    }

    // 9: two lists, each read twice, positions interleaved; a write of each list and a write of both made between
    // (TwiceReadTwoLists).
    static void twoListsTwiceReadWriteBetween() throws Exception {
        var a = PartitionableList.of(16, i -> i);
        var b = PartitionableList.of(16, i -> -i);
        writeBetween("two lists read twice",
                     body -> PartitionableList.runPartitioned(1, a.write(), b.write(), (x, y) -> body.run()),
                     order -> PartitionableList.runPartitioned(1, a.read(), b.read(), a.read(), b.read(),
                                                               (w, x, y, z) -> order.add("call")),
                     List.of(order -> PartitionableList.runPartitioned(1, a.write(), v -> order.add("Wa")),
                             order -> PartitionableList.runPartitioned(1, b.write(), v -> order.add("Wb")),
                             order -> PartitionableList.runPartitioned(1, a.write(), b.write(),
                                                                       (x, y) -> order.add("Wab"))),
                     List.of("Wab"), a, b);
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        System.out.println("TestFairAdmission under " + (FAIR ? "fair admission" : "reader preference"));
        // An argument (0 to 9) runs just that case
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        try {
            if (only < 0 || only == 0) readBehindQueuedWrite();
            if (only < 0 || only == 1) disjointNotDelayed();
            if (only < 0 || only == 2) batonChain();
            if (only < 0 || only == 3) starvation();
            if (only < 0 || only == 4) sameListTwice();
            if (only < 0 || only == 5) randomMix();
            if (only < 0 || only == 6) twiceReadWriteBetween();
            if (only < 0 || only == 7) twinReadWriteRejected();
            if (only < 0 || only == 8) overlapReadsWriteBetween();
            if (only < 0 || only == 9) twoListsTwiceReadWriteBetween();
        } catch (Throwable t) {
            // Exit even when a deadlocked call leaves the pool's threads waiting
            t.printStackTrace();
            System.exit(1);
        }
        System.out.println("TestFairAdmission passed (" + checks + " checks)");
        System.exit(0);
    }
}
