package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.Pool;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The future returned by a single-guard runGuarded is the task's queue node. Keeping it must not keep alive what
 * its task captured, nor the tasks queued on the guard after it (the node's link to its successor), or a program
 * that keeps one early future would keep the whole later history of the guard's queue.
 */
public class TestTaskRetention {
    static final int LATER = 5;

    static boolean cleared(WeakReference<?> w) throws InterruptedException {
        for (int i = 0; i < 50 && w.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        return w.get() == null;
    }

    /** Submits a task on g which captures a fresh object, a weak reference to which is left in ref[0]. */
    static CompletableFuture<Void> submitCapturing(Guard g, WeakReference<?>[] ref) {
        int[] captured = new int[1000];
        ref[0] = new WeakReference<>(captured);
        return g.runGuarded(() -> captured[0]++);
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var g = new Guard();
        var started = new CountDownLatch(1);
        var go = new CountDownLatch(1);
        // Hold g on a pool thread, so that the tasks below queue behind one another (each linked to the next).
        Pool.run(() -> g.runGuarded(() -> {
            started.countDown();
            try {
                go.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
        if (!started.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FAILED: the blocking task never started");
        }

        var capturedRef = new WeakReference[1];
        CompletableFuture<Void> first = submitCapturing(g, capturedRef);
        var second = new WeakReference<CompletableFuture<Void>>(g.runGuarded(() -> { }));
        CompletableFuture<Void> last = null;
        for (int i = 0; i < LATER; i++) {
            last = g.runGuarded(() -> { });
        }
        go.countDown();
        last.get(10, TimeUnit.SECONDS);
        first.get(10, TimeUnit.SECONDS);
        // The guard's tail still refers to the last task, so do one more operation to move it on.
        last = null;
        g.runGuarded(() -> { }).get(10, TimeUnit.SECONDS);

        if (!cleared(capturedRef[0])) {
            throw new AssertionError("FAILED: a kept future keeps what its task captured");
        }
        if (!cleared(second)) {
            throw new AssertionError("FAILED: a kept future keeps the task queued after it");
        }
        if (!first.isDone()) {
            throw new AssertionError("FAILED: first not done");
        }
        System.out.println("TestTaskRetention: ok");
    }
}
