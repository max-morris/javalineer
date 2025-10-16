package edu.lsu.cct.javalineer;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.io.StringWriter;
import java.io.PrintWriter;

public class Pool {
    private static Executor thePool = newDefaultPool();

    /**
     * Retrieves the pool being used by Javalineer.
     *
     * @return The pool.
     */
    public static Executor getPool() {
        return thePool;
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
        String pool = System.getProperty("JAVALINEER_POOL", null);
        if(pool == null || pool.equals("fixed")) {
            return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), getDefaultThreadFactory());
        } else if (pool.equals("debug")) {
            return new DebugPool();
        } else {
            throw new RuntimeException("Invalid JAVALINEER_POOL value: " + pool);
        }
    }
}
