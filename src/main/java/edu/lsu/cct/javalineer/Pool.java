package edu.lsu.cct.javalineer;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class Pool {
    private static Executor thePool = getDefaultPool();

    public static Executor getPool() {
        return thePool;
    }

    public static void setPool(Executor newPool) {
        if (thePool instanceof ExecutorService) {
            ((ExecutorService) thePool).shutdown();
        }
        thePool = newPool;
    }

    public static void run(Runnable task) {
        thePool.execute(task);
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
