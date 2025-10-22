package edu.lsu.cct.javalineer;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.io.StringWriter;
import java.io.PrintWriter;

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

    private static void reallyPrintln(Object o) {
        try (PrintWriter pw = new PrintWriter("/dev/tty")) {
            pw.println(Objects.toString(o));
        } catch (IOException ignored) { }
    }

    public static void execute(Runnable task) {
        thePool.execute(task);
    }

    public static CompletableFuture<Void> run(Runnable task) {
        var done = new CompletableFuture<Void>();

        thePool.execute(() -> {
            try {
                task.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
                var sw = new StringWriter();
                var pw = new PrintWriter(sw);
                pw.println("Pool.run(): Exception in thread " + Thread.currentThread().getName() + ":");
                t.printStackTrace(pw);
                reallyPrintln(sw);
            }
        });

        return done;
    }

    public static <T> CompletableFuture<T> run(Supplier<CompletableFuture<T>> task) {
        var done = new CompletableFuture<T>();

        thePool.execute(() -> {
            task.get().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    done.completeExceptionally(throwable);
                    var sw = new StringWriter();
                    var pw = new PrintWriter(sw);
                    pw.println("Pool.run(): Exception in thread " + Thread.currentThread().getName() + ":");
                    throwable.printStackTrace(pw);
                    reallyPrintln(sw);
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
