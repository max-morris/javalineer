package edu.lsu.cct.javalineer;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Pool {
    private static Executor thePool = initializePool();

    /**
     * Retrieves the pool being used by Javalineer.
     *
     * @return The pool.
     */
    public static Executor getPool() {
        return thePool;
    }

    /**
     * Sets the pool used by Javalineer. This method is only intended for use when nothing is running, and
     * therefore makes no attempt to be thread-safe. If the previous pool is an ExecutorService, shutdown()
     * is called on it.
     * @param newPool The new pool.
     */
    public static void setPool(Executor newPool) {
        setPool(newPool, true);
    }

    /**
     * Sets the pool used by Javalineer. This method is only intended for use when nothing is running, and
     * therefore makes no attempt to be thread-safe.
     * @param newPool The new pool.
     * @param shutdownOld If true, will call shutdown() on the old pool if it is an ExecutorService.
     */
    public static void setPool(Executor newPool, boolean shutdownOld) {
        if (shutdownOld && thePool instanceof ExecutorService) {
            ((ExecutorService) thePool).shutdown();
        }
        thePool = newPool;
    }

    private static volatile Consumer<? super Throwable> taskFailureHandler = null;

    /**
     * Sets a handler which is notified whenever a task run by Javalineer throws. This includes tasks given to
     * {@code runGuarded}, {@code runCondition}, {@code Pool.run}, {@code Loop}, {@link ReadWriteGuard}, and the
     * partitioned list operations. The handler is purely informational: the failure is still captured into the future
     * associated with the task (if there is one), and the handler is not needed for correctness. By default, no
     * handler is installed and failures are reported only through those futures. The handler is called only for
     * exceptions thrown by a task (or a task returning null where a future is required), not when a future returned
     * by a task completes exceptionally.
     * <p>
     * The handler is called synchronously on the thread that ran the failing task, before the task's future is
     * completed exceptionally. When the task was guarded, its guards may still be held while the handler runs, so the
     * handler should be quick and must not wait on work which needs those guards. Any exception thrown by the handler
     * itself is caught and ignored.
     *
     * @param handler The handler, or null to remove the current handler.
     */
    public static void setTaskFailureHandler(Consumer<? super Throwable> handler) {
        taskFailureHandler = handler;
    }

    /**
     * Retrieves the handler set by {@link #setTaskFailureHandler(Consumer)}.
     *
     * @return The current handler, or null if none is installed.
     */
    public static Consumer<? super Throwable> getTaskFailureHandler() {
        return taskFailureHandler;
    }

    /**
     * Notifies the task failure handler, if one is installed, that a task threw. Never throws.
     */
    static void reportTaskFailure(Throwable t) {
        var handler = taskFailureHandler;
        if (handler != null) {
            try {
                handler.accept(t);
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Hands an exception thrown by a raw task (one given directly to an Executor, rather than through Javalineer's
     * future-returning APIs) to the current thread's uncaught exception handler, as a thread pool would. Used by the
     * pools which run tasks in loops that must survive such exceptions. Never throws.
     */
    static void handleUncaught(Throwable t) {
        try {
            var thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, t);
        } catch (Throwable ignored) { }
    }

    /**
     * Runs r, completing done normally if r returns and exceptionally if r throws. Never throws.
     */
    static void runCapturing(Runnable r, CompletableFuture<Void> done) {
        try {
            r.run();
        } catch (Throwable t) {
            reportTaskFailure(t);
            done.completeExceptionally(t);
            return;
        }
        done.complete(null);
    }

    /**
     * Calls task, returning the future it produces. If task throws or returns null, a failed future is returned
     * instead. Never throws.
     */
    static <T> CompletableFuture<T> callCapturing(Supplier<CompletableFuture<T>> task) {
        CompletableFuture<T> fut;
        try {
            fut = task.get();
            if (fut == null) {
                throw new NullPointerException("Task returned a null future");
            }
        } catch (Throwable t) {
            reportTaskFailure(t);
            return CompletableFuture.failedFuture(t);
        }
        return fut;
    }

    public static void execute(Runnable task) {
        thePool.execute(task);
    }

    /**
     * Runs a task on the pool.
     *
     * @param task The task.
     * @return A future which completes when the task returns, or completes exceptionally if the task throws.
     */
    public static CompletableFuture<Void> run(Runnable task) {
        var done = new CompletableFuture<Void>();

        thePool.execute(() -> runCapturing(task, done));

        return done;
    }

    /**
     * Runs a task which produces a future on the pool.
     *
     * @param task The task.
     * @return A future which completes with the result of the future produced by the task. It completes
     *         exceptionally if the task throws, returns null, or produces a future which completes exceptionally.
     */
    public static <T> CompletableFuture<T> run(Supplier<CompletableFuture<T>> task) {
        var done = new CompletableFuture<T>();

        thePool.execute(() -> {
            callCapturing(task).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    done.completeExceptionally(throwable);
                } else {
                    done.complete(result);
                }
            });
        });

        return done;
    }

    private static ThreadFactory getDefaultThreadFactory() {
        return r -> {
            var t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        };
    }

    public static Executor newDefaultPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), getDefaultThreadFactory());
    }

    public static Executor newFixedPool(int nThreads) {
        return Executors.newFixedThreadPool(nThreads, getDefaultThreadFactory());
    }

    public static Executor newDynamicPool() {
        return Executors.newCachedThreadPool(getDefaultThreadFactory());
    }

    public static Executor initializePool() {
        String pool = Optional.ofNullable(System.getProperty("JAVALINEER_POOL", null))
                              .map(String::toLowerCase)
                              .map(String::strip)
                              .orElse("default");

        String nThreadsStr = System.getProperty("JAVALINEER_POOL_THREADS", null);
        Integer nThreads = null;

        try {
            nThreads = Integer.parseInt(nThreadsStr, 10);
        } catch (NumberFormatException ignored) { }

        switch (pool) {
            case "default":
            case "fixed":
                if (nThreads == null) {
                    return newDefaultPool();
                } else {
                    return newFixedPool(nThreads);
                }
            case "debug":
                return new DebugPool();
            case "dynamic":
                return newDynamicPool();
            default:
                throw new RuntimeException("Invalid JAVALINEER_POOL value: " + pool);
        }
    }
}
