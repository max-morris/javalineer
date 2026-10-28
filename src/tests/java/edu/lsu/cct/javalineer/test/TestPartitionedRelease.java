package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.Pool;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A runPartitioned (or reducePartitioned) call's future completes only when the call is entirely over: every part has
 * run and every range it took is released. The releases go through the list's guard and may queue, so the test holds
 * one list's guard (from a plain thread) while the parts finish: the call's future must stay incomplete, normally and
 * when a part throws, until the guard is let go; and when it completes, no range of the call is still held.
 * <p>
 * Before the change, a part signaled the call's latch before releasing, so the future completed with the releases
 * still queued behind the held guard, and every case here fails (run one case with its number, 0 to 4, as the
 * argument).
 */
public class TestPartitionedRelease {
    static final long TIMEOUT_SECONDS = 30;
    static final long SETTLE_MILLIS = 300;
    static int checks = 0;

    static class Boom extends RuntimeException {
        Boom() {
            super("boom");
        }
    }

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    // The list's RangeAccountant, through reflection: it is package-private, and this test is outside the package.
    static Object accountant(PartitionableList<?> list) throws ReflectiveOperationException {
        Field f = PartitionableList.class.getDeclaredField("rangeAccountant");
        f.setAccessible(true);
        return f.get(list);
    }

    // The number of ranges the list's RangeAccountant holds (read and write). Called only while nothing else uses the
    // list: from a continuation of the call's future, which runs after (happens-after) every release of the call.
    static int heldRanges(PartitionableList<?> list) {
        try {
            var gv = accountant(list);
            Field varField = Class.forName("edu.lsu.cct.javalineer.GuardVar").getDeclaredField("var");
            varField.setAccessible(true);
            var var = varField.get(gv);
            // The field, not Var.get, which asserts that this thread holds the guard
            Field data = Class.forName("edu.lsu.cct.javalineer.Var").getDeclaredField("data");
            data.setAccessible(true);
            var ra = data.get(var);
            Field reads = ra.getClass().getDeclaredField("readRanges");
            Field writes = ra.getClass().getDeclaredField("writeRanges");
            reads.setAccessible(true);
            writes.setAccessible(true);
            return ((Map<?, ?>) reads.get(ra)).size() + ((Collection<?>) writes.get(ra)).size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Holds the list's guard from a plain thread until let go; returns once the guard is held.
    static CountDownLatch holdGuard(PartitionableList<?> list) throws Exception {
        var guard = (Guard) accountant(list);
        var held = new CountDownLatch(1);
        var letGo = new CountDownLatch(1);
        var t = new Thread(() -> guard.runGuarded(() -> {
            held.countDown();
            try {
                letGo.await();
            } catch (InterruptedException ignored) {
            }
        }));
        t.setDaemon(true);
        t.start();
        check(held.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the test holds the list's guard");
        return letGo;
    }

    // Starts a call from a plain thread (the last part may run inline on the starting thread, and it blocks).
    static <T> CompletableFuture<T> start(Supplier<CompletableFuture<T>> call) {
        var fut = new CompletableFuture<T>();
        var t = new Thread(() -> call.get().whenComplete((v, e) -> {
            if (e != null) {
                fut.completeExceptionally(e);
            } else {
                fut.complete(v);
            }
        }));
        t.setDaemon(true);
        t.start();
        return fut;
    }

    /**
     * One call over a (read) and b (write), nParts parts, each blocking until let in. Once every part is running,
     * the test takes a's guard, lets the parts finish, and checks that the call isn't over until it lets a's guard go.
     */
    static void runPartitionedCase(String name, int nParts, boolean ranges, boolean throwing) throws Exception {
        var a = PartitionableList.of(64, i -> i);
        var b = PartitionableList.of(64, i -> 0);
        var running = new CountDownLatch(nParts);
        var letIn = new CountDownLatch(1);
        var boom = new Boom();
        var provider = new PartitionRangeProvider() {
            public int numPartitions() {
                return nParts;
            }

            public int getWritableBegin(int p) {
                return PartitionableList.partIndex(p, nParts, 64, 0);
            }

            public int getWritableEnd(int p) {
                return PartitionableList.partIndex(p + 1, nParts, 64, 0);
            }
        };

        var fut = start(() -> {
            if (ranges) {
                return PartitionableList.runPartitioned(provider, a.read(), b.write(), (av, bv) -> {
                    part(av.writableSize(), running, letIn, throwing && av.get(0) == 0, boom);
                    for (int i = 0; i < bv.writableSize(); i++) {
                        bv.set(i, av.get(i) + 1);
                    }
                });
            }
            return PartitionableList.runPartitioned(nParts, a.read(), b.write(), (av, bv) -> {
                part(av.writableSize(), running, letIn, throwing && av.get(0) == 0, boom);
                for (int i = 0; i < bv.writableSize(); i++) {
                    bv.set(i, av.get(i) + 1);
                }
            });
        });
        // What the call's continuation sees: the ranges still held on a and b.
        var seen = new AtomicReference<int[]>();
        var after = fut.handle((v, e) -> {
            seen.set(new int[]{heldRanges(a), heldRanges(b)});
            return e;
        });

        check(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), name + ": every part runs");
        var letGo = holdGuard(a);
        letIn.countDown();
        Thread.sleep(SETTLE_MILLIS);
        check(!fut.isDone(), name + ": the call isn't over while a part's release waits for the list's guard");

        letGo.countDown();
        var failure = after.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(seen.get()[0] == 0 && seen.get()[1] == 0,
              name + ": no range is held when the call's future completes, got " + seen.get()[0] + " and "
                      + seen.get()[1]);
        if (throwing) {
            var cause = failure;
            while (cause != null && cause != boom) {
                cause = cause.getCause();
            }
            check(cause == boom, name + ": the call fails with the part's exception");
        } else {
            check(failure == null, name + ": the call succeeds");
        }
    }

    static void part(int size, CountDownLatch running, CountDownLatch letIn, boolean fail, Boom boom) {
        running.countDown();
        try {
            letIn.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (fail) {
            throw boom;
        }
    }

    /**
     * reducePartitioned: the parts' futures are completed by the test while it holds the list's guard; the result
     * must wait for the releases.
     */
    static void reduceCase() throws Exception {
        int nParts = 4;
        var a = PartitionableList.of(100, i -> i);
        var partFuts = new java.util.concurrent.ConcurrentLinkedQueue<CompletableFuture<Integer>>();
        var started = new CountDownLatch(nParts);
        var result = start(() -> a.reducePartitioned(nParts, view -> {
            int s = 0;
            for (int i = 0; i < view.readableSize(); i++) {
                s += view.get(i);
            }
            if (view.readableSize() == nParts) {
                // The final reduction over the parts' results
                return CompletableFuture.completedFuture(s);
            }
            var f = new CompletableFuture<Integer>();
            partFuts.add(f);
            started.countDown();
            final int sum = s;
            return f.thenApply(x -> sum + x);
        }));
        var seen = new AtomicReference<Integer>();
        var after = result.thenApply(v -> {
            seen.set(heldRanges(a));
            return v;
        });
        check(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "reduce: every part runs");
        var letGo = holdGuard(a);
        // Complete every part's future while the guard is held: each part's release then queues.
        for (var f : partFuts) {
            f.complete(0);
        }
        Thread.sleep(SETTLE_MILLIS);
        check(!result.isDone(), "reduce: the result waits for the parts' releases");
        letGo.countDown();
        int sum = after.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(sum == 99 * 100 / 2, "reduce: the sum, got " + sum);
        check(seen.get() == 0, "reduce: no range is held when the result completes, got " + seen.get());
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        Pool.setTaskFailureHandler(t -> { });
        // An argument (0 to 4) runs just that case, once
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        for (int round = 0; round < (only < 0 ? 3 : 1); round++) {
            if (only < 0 || only == 0) runPartitionedCase("nChunks", 3, false, false);
            if (only < 0 || only == 1) runPartitionedCase("ranges", 3, true, false);
            if (only < 0 || only == 2) runPartitionedCase("nChunks, a part throws", 3, false, true);
            if (only < 0 || only == 3) runPartitionedCase("ranges, a part throws", 3, true, true);
            if (only < 0 || only == 4) reduceCase();
        }
        System.out.println("TestPartitionedRelease passed (" + checks + " checks)");
        System.exit(0);
    }
}
