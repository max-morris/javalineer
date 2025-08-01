package edu.lsu.cct.javalineer;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.io.StringWriter;
import java.io.PrintWriter;

public class Pool {
    private static Executor thePool = getDefaultPool();

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

    public static void run(Runnable task) {
        thePool.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                var sw = new StringWriter();
                var pw = new PrintWriter(sw);
                pw.println("Pool.run(): Exception in thread " + Thread.currentThread().getName() + ":");
                t.printStackTrace(pw);
                System.err.print(sw);
            }
        });
    }

    public static <T> CompletableFuture<T> run(Supplier<CompletableFuture<T>> task) {
        var done = new CompletableFuture<T>();

        thePool.execute(() -> {
            task.get().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    done.completeExceptionally(throwable);
                } else {
                    done.complete(result);
                }
            });
        });

        return done;
    }

    public static ThreadFactory getDefaultThreadFactory() {
        return r -> {
            var t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        };
    }

    public static Executor getDefaultPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), getDefaultThreadFactory());
    }
}
