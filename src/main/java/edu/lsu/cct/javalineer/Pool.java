package edu.lsu.cct.javalineer;

import java.util.concurrent.*;

public class Pool {
    private static Executor POOL = getDefaultPool();

    public static Executor getPool() {
        return POOL;
    }

    public static void setPool(Executor newPool) {
        if (POOL instanceof ExecutorService) {
            ((ExecutorService) POOL).shutdown();
        }
        POOL = newPool;
    }

    public static void run(Runnable r) {
        POOL.execute(r);
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
