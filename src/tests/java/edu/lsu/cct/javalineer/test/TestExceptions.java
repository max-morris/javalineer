package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks that exceptions thrown by tasks are captured into the tasks' futures, never escape into unrelated code,
 * and never leave guards locked.
 */
public class TestExceptions {
    static final long TIMEOUT_SECONDS = 10;

    static class Boom extends RuntimeException {
        Boom(String msg) {
            super(msg);
        }
    }

    static int checks = 0;

    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
        checks++;
    }

    static <T> T await(CompletableFuture<T> f, String what) {
        try {
            return f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("FAILED (timed out): " + what);
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("FAILED (" + e + "): " + what, e);
        }
    }

    static Throwable unwrap(Throwable t) {
        while (t instanceof ExecutionException || t instanceof java.util.concurrent.CompletionException) {
            if (t.getCause() == null) {
                break;
            }
            t = t.getCause();
        }
        return t;
    }

    /** Waits for f to fail, and checks that it failed with exactly the given exception. */
    static void awaitFailure(CompletableFuture<?> f, Throwable expected, String what) {
        try {
            f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("FAILED (timed out, future still pending): " + what);
        } catch (InterruptedException e) {
            throw new AssertionError("FAILED (interrupted): " + what);
        } catch (ExecutionException e) {
            var cause = unwrap(e);
            check(expected == null || cause == expected, what + " (failed with " + cause + ")");
            return;
        }
        throw new AssertionError("FAILED (completed normally): " + what);
    }

    /** Checks that a guard (or guard set) is not stuck: a new task on it runs and completes. */
    static void checkUsable(GuardSet gs, String what) {
        var ran = new AtomicBoolean(false);
        await(Guard.runGuarded(gs, () -> ran.set(true)), what);
        check(ran.get(), what);
    }

    static void checkUsable(Guard g, String what) {
        checkUsable(GuardSet.of(g), what);
    }

    static void singleGuard() {
        var g = new Guard();

        // Uncontended: the task runs inline on this thread. It must not throw into us.
        var boom = new Boom("single inline");
        CompletableFuture<Void> f;
        try {
            f = g.runGuarded(() -> {
                throw boom;
            });
        } catch (Throwable t) {
            throw new AssertionError("FAILED: runGuarded threw into its caller: " + t, t);
        }
        check(f.isCompletedExceptionally(), "inline runGuarded is already failed");
        awaitFailure(f, boom, "inline runGuarded fails its future");
        checkUsable(g, "guard usable after inline failure");

        // Same via the static entry points and with an Error rather than an Exception
        var err = new Error("single static");
        awaitFailure(Guard.runGuarded(g, () -> {
            throw err;
        }), err, "static runGuarded(Guard, Runnable) captures an Error");
        checkUsable(g, "guard usable after Error");

        var boom2 = new Boom("varargs single");
        awaitFailure(Guard.runGuarded(() -> {
            throw boom2;
        }, g), boom2, "runGuarded(Runnable, Guard...) with one guard");
        checkUsable(g, "guard usable after varargs failure");

        var boom3 = new Boom("zero guards");
        CompletableFuture<Void> f3;
        try {
            f3 = Guard.runGuarded(() -> {
                throw boom3;
            }, new Guard[0]);
        } catch (Throwable t) {
            throw new AssertionError("FAILED: runGuarded with zero guards threw into its caller: " + t, t);
        }
        awaitFailure(f3, boom3, "runGuarded(Runnable, Guard...) with zero guards");

        var boom3b = new Boom("empty guard set");
        awaitFailure(Guard.runGuarded(GuardSet.of(), () -> {
            throw boom3b;
        }), boom3b, "runGuarded(GuardSet, Runnable) with an empty set");

        // GuardVar
        var gv = new GuardVar<>(0);
        var boom4 = new Boom("guard var");
        awaitFailure(Guard.runGuarded(gv, v -> {
            v.set(1);
            throw boom4;
        }), boom4, "runGuarded(GuardVar, GuardTask1)");
        var seen = new AtomicInteger(-1);
        await(Guard.runGuarded(gv, v -> seen.set(v.get())), "GuardVar usable after failure");
        check(seen.get() == 1, "GuardVar keeps writes made before the throw");

        try {
            gv.runGuarded(v -> {
                throw new Boom("guard var consumer");
            });
        } catch (Throwable t) {
            throw new AssertionError("FAILED: GuardVar.runGuarded threw into its caller: " + t, t);
        }
        checkUsable(gv, "GuardVar usable after Consumer failure");
    }

    static void multiGuard() {
        var g1 = new Guard();
        var g2 = new Guard();
        var g3 = new Guard();
        var gs = GuardSet.of(g1, g2, g3);

        var boom = new Boom("multi");
        CompletableFuture<Void> f;
        try {
            f = Guard.runGuarded(gs, () -> {
                throw boom;
            });
        } catch (Throwable t) {
            throw new AssertionError("FAILED: multi-guard runGuarded threw into its caller: " + t, t);
        }
        awaitFailure(f, boom, "multi-guard runGuarded fails its future");
        checkUsable(g1, "g1 usable after multi-guard failure");
        checkUsable(g2, "g2 usable after multi-guard failure");
        checkUsable(g3, "g3 usable after multi-guard failure");
        checkUsable(gs, "{g1, g2, g3} usable after multi-guard failure");
        checkUsable(GuardSet.of(g1, g3), "{g1, g3} usable after multi-guard failure");

        var boom2 = new Boom("multi varargs");
        awaitFailure(Guard.runGuarded(() -> {
            throw boom2;
        }, g2, g3), boom2, "runGuarded(Runnable, Guard...) with two guards");
        checkUsable(gs, "{g1, g2, g3} usable after varargs failure");

        var gv1 = new GuardVar<>("a");
        var gv2 = new GuardVar<>("b");
        var boom3 = new Boom("multi guard var");
        awaitFailure(Guard.runGuarded(gv1, gv2, (a, b) -> {
            throw boom3;
        }), boom3, "runGuarded(GuardVar, GuardVar, GuardTask2)");
        checkUsable(gv1, "gv1 usable after failure");
        checkUsable(gv2, "gv2 usable after failure");
        checkUsable(GuardSet.of(gv1, gv2), "{gv1, gv2} usable after failure");

        // Many failing multi-guard tasks in parallel, interleaved with succeeding ones
        final int n = 1000;
        var futs = new ArrayList<CompletableFuture<Void>>();
        var counter = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            final int i_ = i;
            futs.add(Pool.run(() -> Guard.runGuarded(i_ % 2 == 0 ? gs : GuardSet.of(g1, g3), () -> {
                counter.incrementAndGet();
                if (i_ % 3 == 0) {
                    throw new Boom("parallel " + i_);
                }
            })));
        }
        for (int i = 0; i < n; i++) {
            if (i % 3 == 0) {
                awaitFailure(futs.get(i), null, "parallel multi-guard task " + i + " fails");
            } else {
                await(futs.get(i), "parallel multi-guard task " + i + " completes");
            }
        }
        checkUsable(gs, "{g1, g2, g3} usable after many parallel failures");
        check(counter.get() == n, "all parallel multi-guard tasks ran");
    }

    /**
     * A task is held on a guard while more tasks, some of which throw, queue up behind it. When the first task
     * finishes, the thread running it hands off to the queued tasks; their exceptions must not break the chain or
     * escape into that thread.
     */
    static void handOffChain() throws InterruptedException {
        for (var multi : new boolean[] {false, true}) {
            var g = new Guard();
            var other = new Guard();
            var gs = multi ? GuardSet.of(g, other) : GuardSet.of(g);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var leaked = new AtomicReference<Throwable>();

            var holder = new Thread(() -> {
                try {
                    Guard.runGuarded(gs, () -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ignored) { }
                    });
                } catch (Throwable t) {
                    leaked.set(t);
                }
            });
            holder.setDaemon(true);
            holder.start();
            started.await();

            final int n = 10;
            var order = new ConcurrentLinkedQueue<Integer>();
            var futs = new ArrayList<CompletableFuture<Void>>();
            var booms = new ArrayList<Boom>();
            for (int i = 0; i < n; i++) {
                final int i_ = i;
                var boom = new Boom("queued " + i);
                booms.add(boom);
                futs.add(Guard.runGuarded(gs, () -> {
                    order.add(i_);
                    if (i_ % 2 == 0) {
                        throw boom;
                    }
                }));
            }
            check(futs.stream().noneMatch(CompletableFuture::isDone), "queued tasks wait for the holder");

            release.countDown();
            holder.join(TIMEOUT_SECONDS * 1000);
            check(!holder.isAlive(), "holder thread finished");
            check(leaked.get() == null, "no exception leaked into the thread that ran the hand-off: " + leaked.get());

            for (int i = 0; i < n; i++) {
                if (i % 2 == 0) {
                    awaitFailure(futs.get(i), booms.get(i), "queued task " + i + " fails");
                } else {
                    await(futs.get(i), "queued task " + i + " completes");
                }
            }
            var expectedOrder = new ArrayList<Integer>();
            for (int i = 0; i < n; i++) {
                expectedOrder.add(i);
            }
            check(new ArrayList<>(order).equals(expectedOrder), "queued tasks ran in FIFO order: " + order);
            checkUsable(gs, "guards usable after the hand-off chain");
            checkUsable(g, "guard usable after the hand-off chain");
            checkUsable(other, "other guard usable after the hand-off chain");
        }
    }

    static void conditions() {
        var gv = new GuardVar<>(0);
        var cond = Guard.newCondition(gv);

        // Throws immediately
        var boom = new Boom("condition immediate");
        awaitFailure(Guard.runCondition(cond, v -> {
            throw boom;
        }), boom, "condition task throwing immediately fails its future");
        checkUsable(gv, "guard usable after condition failure");

        // Waits, then throws when re-run by signal(); an Error this time
        var err = new Error("condition later");
        var f = Guard.runCondition(cond, v -> {
            if (v.get() == 0) {
                return false;
            }
            throw err;
        });
        var waiting = Guard.runCondition(cond, v -> v.get() == 2);
        check(!f.isDone(), "condition task is pending");
        await(Guard.runGuarded(gv, v -> v.set(1)), "set to 1");
        cond.signal();
        awaitFailure(f, err, "condition task throwing on signal() fails its future");
        check(!waiting.isDone(), "other condition task still waiting");

        // signalAll() keeps going past a throwing task
        var boom2 = new Boom("condition signalAll");
        var f2 = Guard.runCondition(cond, v -> {
            if (v.get() < 2) {
                return false;
            }
            throw boom2;
        });
        var f3 = Guard.runCondition(cond, v -> v.get() == 2);
        await(Guard.runGuarded(gv, v -> v.set(2)), "set to 2");
        cond.signalAll();
        awaitFailure(f2, boom2, "condition task throwing on signalAll() fails its future");
        await(waiting, "earlier waiting condition task completes");
        await(f3, "later condition task still runs after a throwing one");
        checkUsable(gv, "guard usable after condition failures");

        // Multiple guards
        var gv2 = new GuardVar<>(0);
        var cond2 = Guard.newCondition(gv, gv2);
        var boom3 = new Boom("multi condition");
        awaitFailure(Guard.runCondition(cond2, (a, b) -> {
            throw boom3;
        }), boom3, "multi-guard condition task fails its future");
        checkUsable(GuardSet.of(gv, gv2), "guards usable after multi-guard condition failure");
    }

    static void pool() {
        var boom = new Boom("pool runnable");
        awaitFailure(Pool.run(() -> {
            throw boom;
        }), boom, "Pool.run(Runnable) throwing");

        var boom2 = new Boom("pool supplier throws");
        awaitFailure(Pool.run(() -> {
            if (boom2 != null) {
                throw boom2;
            }
            return CompletableFuture.completedFuture(1);
        }), boom2, "Pool.run(Supplier) throwing");

        var boom3 = new Boom("pool supplier future fails");
        awaitFailure(Pool.run(() -> CompletableFuture.failedFuture(boom3)), boom3,
                     "Pool.run(Supplier) whose future fails");

        awaitFailure(Pool.run(() -> (CompletableFuture<Integer>) null), null, "Pool.run(Supplier) returning null");

        check(await(Pool.run(() -> CompletableFuture.completedFuture(42)), "Pool.run(Supplier) normal") == 42,
              "Pool.run(Supplier) result");

        // The pool still works after many failures
        var futs = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 1000; i++) {
            futs.add(Pool.run(() -> {
                throw new Boom("many");
            }));
        }
        for (var f : futs) {
            awaitFailure(f, null, "many Pool.run failures");
        }
        var ran = new AtomicBoolean();
        await(Pool.run(() -> ran.set(true)), "pool usable after failures");
        check(ran.get(), "pool usable after failures");
    }

    static void loops() throws InterruptedException {
        var boom = new Boom("parFor body throws");
        var count = new AtomicInteger();
        awaitFailure(Loop.parFor(0, 100, i -> {
            count.incrementAndGet();
            if (i == 37) {
                throw boom;
            }
            return CompletableFuture.completedFuture(null);
        }), boom, "parFor with a throwing body");

        var boom2 = new Boom("parFor body future fails");
        awaitFailure(Loop.parFor(0, 100, i -> {
            if (i == 42) {
                var f = new CompletableFuture<Void>();
                Pool.run(() -> f.completeExceptionally(boom2));
                return f;
            }
            return CompletableFuture.completedFuture(null);
        }), boom2, "parFor with a failing body future");

        // A body future that never completes keeps parFor pending
        var pending = Loop.parFor(0, 10, i -> i == 3 ? new CompletableFuture<>() : CompletableFuture.completedFuture(null));
        Thread.sleep(200);
        check(!pending.isDone(), "parFor stays pending while a body future is pending");

        await(Loop.parFor(0, 100, i -> CompletableFuture.completedFuture(null)), "parFor normal");
        await(Loop.parFor(0, 0, i -> CompletableFuture.completedFuture(null)), "parFor empty");

        var boom3 = new Boom("marchingFor body throws");
        var reached = new AtomicInteger(-1);
        awaitFailure(Loop.marchingFor(0, 10, i -> {
            reached.set(i);
            if (i == 5) {
                throw boom3;
            }
            return CompletableFuture.completedFuture(null);
        }), boom3, "marchingFor with a throwing body");
        check(reached.get() == 5, "marchingFor stops at the failing iteration");

        var boom4 = new Boom("marchingFor body future fails");
        awaitFailure(Loop.marchingFor(0, 10, i -> i == 7 ? CompletableFuture.failedFuture(boom4)
                                                       : CompletableFuture.completedFuture(null)),
                     boom4, "marchingFor with a failing body future");
    }

    static void partitioned() {
        var list = PartitionableList.of(100, i -> i);

        var boom = new Boom("runPartitioned chunk");
        awaitFailure(PartitionableList.runPartitioned(4, list.readWrite(), view -> {
            if (view.writableSize() > 0 && view.get(0) == 50) {
                throw boom;
            }
        }), boom, "runPartitioned with a throwing chunk");
        // The failing chunk's range was released, so the list is usable
        await(PartitionableList.runPartitioned(4, list.readWrite(), view -> {
            for (int i = 0; i < view.writableSize(); i++) {
                view.set(i, view.get(i) + 1);
            }
        }), "runPartitioned after a failure");

        var boom2 = new Boom("runPartitionedFut chunk");
        awaitFailure(PartitionableListExt.runPartitionedFut(4, list.readWrite(), view -> {
            if (view.get(0) == 26) {
                return CompletableFuture.failedFuture(boom2);
            }
            return CompletableFuture.completedFuture(null);
        }), boom2, "runPartitionedFut with a failing chunk future");

        var boom3 = new Boom("runPartitionedFut chunk throws");
        awaitFailure(PartitionableListExt.runPartitionedFut(4, list.readWrite(), view -> {
            if (view.get(0) == 1) {
                throw boom3;
            }
            return CompletableFuture.completedFuture(null);
        }), boom3, "runPartitionedFut with a throwing chunk");

        var boom4 = new Boom("reducePartitioned chunk");
        awaitFailure(list.reducePartitioned(4, view -> {
            if (view.readableSize() > 1 && view.get(0) == 76) {
                throw boom4;
            }
            int sum = 0;
            for (int i = 0; i < view.readableSize(); i++) {
                sum += view.get(i);
            }
            return CompletableFuture.completedFuture(sum);
        }), boom4, "reducePartitioned with a throwing chunk");

        int sum = await(list.reducePartitioned(4, view -> {
            int s = 0;
            for (int i = 0; i < view.readableSize(); i++) {
                s += view.get(i);
            }
            return CompletableFuture.completedFuture(s);
        }), "reducePartitioned after failures");
        check(sum == 100 * 101 / 2, "reducePartitioned result " + sum);
    }

    static void readWriteGuard() {
        var rw = new ReadWriteGuard();
        var boom = new Boom("write");
        awaitFailure(rw.runWriteGuarded(() -> {
            throw boom;
        }), boom, "throwing write task");
        var boom2 = new Boom("read");
        awaitFailure(rw.runReadGuarded(() -> {
            throw boom2;
        }), boom2, "throwing read task");
        var n = new AtomicInteger();
        await(rw.runWriteGuarded(n::incrementAndGet), "write after failures");
        await(rw.runReadGuarded(n::incrementAndGet), "read after failures");
        check(n.get() == 2, "read/write guard usable after failures");
    }

    static void failureHandler() {
        var seen = new ConcurrentLinkedQueue<Throwable>();
        var threads = new ConcurrentLinkedQueue<Thread>();
        Pool.setTaskFailureHandler(t -> {
            seen.add(t);
            threads.add(Thread.currentThread());
        });
        try {
            check(Pool.getTaskFailureHandler() != null, "handler installed");

            var g = new Guard();
            var boom1 = new Boom("handler guard");
            awaitFailure(g.runGuarded(() -> {
                throw boom1;
            }), boom1, "guard failure with handler");
            check(seen.contains(boom1), "handler saw the guarded task's exception");
            check(threads.contains(Thread.currentThread()), "handler ran on the thread that ran the inline task");

            var boom2 = new Boom("handler pool");
            awaitFailure(Pool.run(() -> {
                throw boom2;
            }), boom2, "pool failure with handler");
            check(seen.contains(boom2), "handler saw the pool task's exception");

            var gv = new GuardVar<>(0);
            var boom3 = new Boom("handler condition");
            awaitFailure(Guard.runCondition(Guard.newCondition(gv), v -> {
                throw boom3;
            }), boom3, "condition failure with handler");
            check(seen.contains(boom3), "handler saw the condition task's exception");

            var boom4 = new Boom("handler multi");
            awaitFailure(Guard.runGuarded(GuardSet.of(g, gv), () -> {
                throw boom4;
            }), boom4, "multi-guard failure with handler");
            check(seen.contains(boom4), "handler saw the multi-guard task's exception");

            var boom5 = new Boom("handler parFor");
            awaitFailure(Loop.parFor(0, 4, i -> {
                if (i == 2) {
                    throw boom5;
                }
                return CompletableFuture.completedFuture(null);
            }), boom5, "parFor failure with handler");
            check(seen.contains(boom5), "handler saw the parFor body's exception");

            int before = seen.size();
            await(g.runGuarded(() -> { }), "successful task with handler");
            check(seen.size() == before, "handler not called for a successful task");

            // A handler which throws must not break anything
            Pool.setTaskFailureHandler(t -> {
                throw new Boom("handler itself");
            });
            var boom6 = new Boom("throwing handler");
            awaitFailure(g.runGuarded(() -> {
                throw boom6;
            }), boom6, "failure with a throwing handler");
            checkUsable(g, "guard usable with a throwing handler");
        } finally {
            Pool.setTaskFailureHandler(null);
        }
        check(Pool.getTaskFailureHandler() == null, "handler removed");
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();

        singleGuard();
        multiGuard();
        handOffChain();
        conditions();
        pool();
        loops();
        partitioned();
        readWriteGuard();
        failureHandler();

        System.out.println("TestExceptions: all " + checks + " checks passed");
    }
}
