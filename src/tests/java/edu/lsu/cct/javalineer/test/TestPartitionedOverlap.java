package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.PartitionableListExt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Overlapping parts in the range-provider form (PartCall.ofRanges). Nothing admits a call's parts against each other,
 * so two parts writing the same cells would race: the call is rejected at once, with IllegalArgumentException, having
 * admitted nothing. Parts that are adjacent, disjoint, empty, or only read pass, and so do the (nParts, ghosts) forms.
 */
public class TestPartitionedOverlap {
    static final long TIMEOUT_SECONDS = 30;
    static int checks = 0;

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static PartitionRangeProvider ranges(int[]... r) {
        return new PartitionRangeProvider() {
            public int numPartitions() {
                return r.length;
            }

            public int getWritableBegin(int p) {
                return r[p][0];
            }

            public int getWritableEnd(int p) {
                return r[p][1];
            }
        };
    }

    static int[] r(int begin, int end) {
        return new int[] {begin, end};
    }

    static Throwable thrownBy(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    static CompletableFuture<Void> noFut() {
        return CompletableFuture.completedFuture(null);
    }

    /** Every way of writing through parts of p: runPartitioned and runPartitionedFut, write and read-write. */
    static Runnable[] writeCalls(PartitionRangeProvider p, PartitionableList<Integer> x, AtomicInteger ran) {
        return new Runnable[] {
            () -> PartitionableList.runPartitioned(p, x.write(), v -> {
                ran.incrementAndGet();
            }),
            () -> PartitionableList.runPartitioned(p, x.readWrite(), v -> {
                ran.incrementAndGet();
            }),
            () -> PartitionableListExt.runPartitionedFut(p, x.write(), v -> {
                ran.incrementAndGet();
                return noFut();
            }),
            () -> PartitionableListExt.runPartitionedFut(p, x.readWrite(), v -> {
                ran.incrementAndGet();
                return noFut();
            }),
        };
    }

    static void checkRejected(String name, PartitionRangeProvider p, String... inMessage) throws Exception {
        var x = PartitionableList.of(16, i -> i);
        var ran = new AtomicInteger();
        for (var call : writeCalls(p, x, ran)) {
            var t = thrownBy(call);
            check(t instanceof IllegalArgumentException, name + ": rejected, got " + t);
            for (var s : inMessage) {
                check(t.getMessage().contains(s), name + ": \"" + t.getMessage() + "\" names " + s);
            }
            check(TestPartitionedRelease.heldRanges(x) == 0, name + ": nothing admitted");
        }
        check(ran.get() == 0, name + ": no part runs");
        // Nothing is left behind: a call on the whole list goes straight in.
        PartitionableList.runPartitioned(ranges(r(0, 16)), x.write(), v -> {
            ran.incrementAndGet();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ran.get() == 1, name + ": the list is free afterwards");
    }

    static void overlapRejected() throws Exception {
        checkRejected("partial", ranges(r(0, 4), r(2, 6)), "[0, 4)", "[2, 6)");
        checkRejected("listed backwards", ranges(r(2, 6), r(0, 4)), "[0, 4)", "[2, 6)");
        checkRejected("identical", ranges(r(1, 3), r(1, 3)), "[1, 3)");
        checkRejected("one inside another", ranges(r(0, 10), r(2, 3)), "[0, 10)", "[2, 3)");
        checkRejected("same start", ranges(r(4, 6), r(4, 5)), "[4, 6)", "[4, 5)");
        checkRejected("away from the first part", ranges(r(0, 2), r(8, 12), r(4, 8), r(11, 12)), "[8, 12)", "[11, 12)");
        checkRejected("around an empty one", ranges(r(0, 4), r(2, 2), r(3, 5)), "[0, 4)", "[3, 5)");
        checkRejected("negative starts", ranges(r(-4, 0), r(-2, 1)), "[-4, 0)", "[-2, 1)");
        // A read of another list changes nothing: the ranges are the same in every list, and one list is written.
        var a = PartitionableList.of(16, i -> i);
        var b = PartitionableList.of(16, i -> 0);
        var mixed = thrownBy(() -> PartitionableList.runPartitioned(ranges(r(0, 4), r(2, 6)), a.read(), b.write(),
                                                                    (av, bv) -> { }));
        check(mixed instanceof IllegalArgumentException, "a read and a write of two lists: rejected, got " + mixed);
        check(TestPartitionedRelease.heldRanges(a) == 0 && TestPartitionedRelease.heldRanges(b) == 0,
              "a read and a write of two lists: nothing admitted");
    }

    /** Runs each form on p, writing every cell of each part's view once, and checks it wrote each covered cell once. */
    static void checkWrites(String name, PartitionRangeProvider p) throws Exception {
        int covered = 0;
        for (int q = 0; q < p.numPartitions(); q++) {
            covered += Math.max(0, p.getWritableEnd(q) - p.getWritableBegin(q));
        }
        for (int form = 0; form < 4; form++) {
            var x = PartitionableList.of(16, i -> 0);
            var cells = new AtomicInteger();
            CompletableFuture<Void> fut;
            switch (form) {
                case 0:
                    fut = PartitionableList.runPartitioned(p, x.write(), v -> {
                        for (int i = 0; i < v.writableSize(); i++) {
                            v.set(i, 1);
                            cells.incrementAndGet();
                        }
                    });
                    break;
                case 1:
                    fut = PartitionableList.runPartitioned(p, x.readWrite(), v -> {
                        for (int i = 0; i < v.writableSize(); i++) {
                            v.set(i, 1 + v.get(i));
                            cells.incrementAndGet();
                        }
                    });
                    break;
                case 2:
                    fut = PartitionableListExt.runPartitionedFut(p, x.write(), v -> {
                        for (int i = 0; i < v.writableSize(); i++) {
                            v.set(i, 1);
                            cells.incrementAndGet();
                        }
                        return noFut();
                    });
                    break;
                default:
                    fut = PartitionableListExt.runPartitionedFut(p, x.readWrite(), v -> {
                        for (int i = 0; i < v.writableSize(); i++) {
                            v.set(i, 1 + v.get(i));
                            cells.incrementAndGet();
                        }
                        return noFut();
                    });
                    break;
            }
            fut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(cells.get() == covered, name + " form " + form + ": wrote " + cells.get() + " of " + covered);
            check(TestPartitionedRelease.heldRanges(x) == 0, name + " form " + form + ": released");
        }
    }

    static void disjointPass() throws Exception {
        checkWrites("adjacent", ranges(r(0, 4), r(4, 8), r(8, 16)));
        checkWrites("adjacent backwards", ranges(r(8, 16), r(4, 8), r(0, 4)));
        checkWrites("gaps", ranges(r(0, 2), r(5, 6), r(10, 16)));
        checkWrites("empty ranges inside others", ranges(r(0, 8), r(3, 3), r(8, 8), r(8, 16), r(12, 12)));
        checkWrites("only empty ranges", ranges(r(3, 3), r(3, 3)));
        checkWrites("one part", ranges(r(0, 16)));
        checkWrites("no parts", ranges());
        // MergeSort's generated calls.
        checkWrites("MergeSort's halves", TestPartitionedFut.halves(0, 16));
        checkWrites("MergeSort's halves, odd", TestPartitionedFut.halves(3, 12));
    }

    static void overlappingReadsPass() throws Exception {
        var x = PartitionableList.of(16, i -> i);
        var y = PartitionableList.of(16, i -> i);
        var overlapping = ranges(r(0, 4), r(2, 6), r(2, 6));
        var ran = new AtomicInteger();
        PartitionableList.runPartitioned(overlapping, x.read(), v -> {
            check(v.get(0) == v.sourceBegin(), "a part reads its own range");
            ran.incrementAndGet();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableList.runPartitioned(overlapping, x.read(), y.read(), (v, w) -> {
            ran.incrementAndGet();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableListExt.runPartitionedFut(overlapping, x.read(), v -> {
            ran.incrementAndGet();
            return noFut();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // A list given twice for reading, overlapping as well.
        PartitionableList.runPartitioned(overlapping, x.read(), x.read(), (v, w) -> {
            ran.incrementAndGet();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ran.get() == 12, "every overlapping read part ran, got " + ran.get());
        check(TestPartitionedRelease.heldRanges(x) == 0 && TestPartitionedRelease.heldRanges(y) == 0, "released");
    }

    /** Ghost-widened reads overlap each other and the write ranges of other parts; the writable ranges do not. */
    static void ghostsPass() throws Exception {
        for (int size : new int[] {0, 1, 2, 3, 7, 16, 17, 100}) {
            for (int nParts : new int[] {1, 2, 3, 5, 8, 16}) {
                for (int g : new int[] {0, 1, 2, 3}) {
                    // Monotone with room for the ghosts; without room, every writable range is empty.
                    for (int p = 0; p < nParts; p++) {
                        int lo = PartitionableList.partIndex(p, nParts, size, g);
                        int hi = PartitionableList.partIndex(p + 1, nParts, size, g);
                        check(size >= 2 * g ? lo <= hi : hi <= lo, "parts of " + size + " split " + nParts + " ghosts " + g);
                    }
                }
            }
        }
        int n = 32, g = 2, nParts = 4;
        var src = PartitionableList.of(n, i -> i);
        var dst = PartitionableList.of(n, i -> 0);
        var mid = PartitionableList.of(n, i -> 0);
        // A stencil through (nParts, ghosts): every part reads into its neighbors' ranges, and writes its own.
        PartitionableList.runPartitioned(nParts, g, src.read(), dst.write(), (s, d) -> {
            for (int i = 0; i < d.writableSize(); i++) {
                d.set(i, s.get(i - g) + s.get(i + g));
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableListExt.runPartitionedFut(nParts, g, src.read(), mid.write(), (s, d) -> {
            for (int i = 0; i < d.writableSize(); i++) {
                d.set(i, s.get(i - g) + s.get(i + g));
            }
            return noFut();
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // The same through (ranges, ghosts), with adjacent parts; overlapping write parts are rejected as ever.
        var viaRanges = PartitionableList.of(n, i -> 0);
        PartitionableList.runPartitioned(ranges(r(2, 12), r(12, 20), r(20, 30)), g, src.read(), viaRanges.write(), (s, d) -> {
            for (int i = 0; i < d.writableSize(); i++) {
                d.set(i, s.get(i - g) + s.get(i + g));
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var overlap = thrownBy(() -> PartitionableList.runPartitioned(ranges(r(2, 16), r(10, 30)), g, src.read(),
                                                                      viaRanges.write(), (s, d) -> { }));
        check(overlap instanceof IllegalArgumentException, "overlapping write parts with ghosts: rejected, got " + overlap);
        // Overlapping parts with ghosts, only reading.
        PartitionableList.runPartitioned(ranges(r(2, 16), r(10, 30)), g, src.read(), v -> {
            check(v.get(-g) == v.sourceBegin(), "a read part's ghost");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (int i = g; i < n - g; i++) {
            check(value(dst, i) == 2 * i, "the stencil's value at " + i);
            check(value(mid, i) == 2 * i, "the Fut stencil's value at " + i);
            check(value(viaRanges, i) == 2 * i, "the (ranges, ghosts) stencil's value at " + i);
        }
        check(src.reducePartitioned(nParts, g, v -> CompletableFuture.completedFuture(0))
                 .get(TIMEOUT_SECONDS, TimeUnit.SECONDS) == 0, "a reduce over ghosts");
        check(TestPartitionedRelease.heldRanges(src) == 0 && TestPartitionedRelease.heldRanges(dst) == 0, "released");
    }

    static int value(PartitionableList<Integer> list, int i) throws Exception {
        var out = new AtomicInteger();
        PartitionableList.runPartitioned(ranges(r(i, i + 1)), list.read(), v -> {
            out.set(v.get(0));
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return out.get();
    }

    /** Read and write intents on one list: the rejection it always had, also over parts that overlap, and at no parts. */
    static void readAndWriteOfOneList() throws Exception {
        var x = PartitionableList.of(16, i -> i);
        for (var p : new PartitionRangeProvider[] {ranges(r(0, 8), r(8, 16)), ranges(r(0, 4), r(2, 6)), ranges()}) {
            var t = thrownBy(() -> PartitionableList.runPartitioned(p, x.read(), x.write(), (a, b) -> { }));
            check(t instanceof IllegalArgumentException && t.getMessage().contains("appears more than once"),
                  "a list read and written in one call, got " + t);
            var f = thrownBy(() -> PartitionableListExt.runPartitionedFut(p, x.write(), x.read(), (a, b) -> noFut()));
            check(f instanceof IllegalArgumentException && f.getMessage().contains("appears more than once"),
                  "a list written and read in one Fut call, got " + f);
        }
        check(TestPartitionedRelease.heldRanges(x) == 0, "nothing admitted");
        var negative = thrownBy(() -> PartitionableList.runPartitioned(new PartitionRangeProvider() {
            public int numPartitions() {
                return -1;
            }

            public int getWritableBegin(int p) {
                return 0;
            }

            public int getWritableEnd(int p) {
                return 0;
            }
        }, x.write(), v -> { }));
        check(negative instanceof IllegalArgumentException, "a negative part count, got " + negative);
    }

    /** A provider changing after the call has no effect: a call runs the parts it checked and admitted. */
    static void providerReadOnce() throws Exception {
        var x = PartitionableList.of(16, i -> 0);
        var r = new int[][] {r(0, 4), r(4, 8)};
        var changed = new CountDownLatch(1);
        var seen = new ConcurrentHashMap<Integer, Integer>();
        var fut = PartitionableList.runPartitioned(ranges(r), x.write(), v -> {
            if (v.partitionNum() == 0) {
                r[1] = r(0, 16);
                changed.countDown();
            } else {
                await(changed);
            }
            seen.put(v.partitionNum(), v.sourceBegin() * 100 + v.writableSize());
        });
        fut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(seen.get(0) == 4 && seen.get(1) == 404, "the parts checked at the call ran, got " + seen);
    }

    static void await(CountDownLatch l) {
        try {
            l.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The rejection fails whichever body makes the call: a guard that body holds is released as usual, and an enclosing
     * call's part fails with it and that call's ranges are released.
     */
    static void fromInsideABody() throws Exception {
        var x = PartitionableList.of(16, i -> 0);
        var y = PartitionableList.of(16, i -> 0);
        var g = new GuardVar<>(0);
        var inGuard = new AtomicReference<Throwable>();
        Guard.runGuarded(g, v -> {
            inGuard.set(thrownBy(() -> PartitionableList.runPartitioned(ranges(r(0, 4), r(2, 6)), x.write(), w -> { })));
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(inGuard.get() instanceof IllegalArgumentException, "rejected inside a guard's body, got " + inGuard.get());
        Guard.runGuarded(g, v -> v.set(1)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var uncaught = Guard.runGuarded(g, v -> {
            PartitionableList.runPartitioned(ranges(r(0, 4), r(2, 6)), x.write(), w -> { });
        });
        check(uncaught.handle((v, t) -> t).get(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null, "an uncaught rejection fails the body");
        Guard.runGuarded(g, v -> v.set(2)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(TestPartitionedRelease.heldRanges(x) == 0, "nothing admitted from a guard's body");

        var outer = PartitionableList.runPartitioned(2, y.write(), v -> {
            PartitionableList.runPartitioned(ranges(r(0, 4), r(2, 6)), x.write(), w -> { });
        });
        var failure = thrownBy(() -> outer.join());
        check(failure != null && failure.getCause() instanceof IllegalArgumentException,
              "an enclosing part fails with the rejection, got " + failure);
        check(TestPartitionedRelease.heldRanges(y) == 0 && TestPartitionedRelease.heldRanges(x) == 0,
              "the enclosing call released its ranges and the rejected one took none");
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        overlapRejected();
        disjointPass();
        overlappingReadsPass();
        ghostsPass();
        readAndWriteOfOneList();
        providerReadOnce();
        fromInsideABody();
        System.out.println("TestPartitionedOverlap: " + checks + " checks passed");
        System.exit(0);
    }
}
