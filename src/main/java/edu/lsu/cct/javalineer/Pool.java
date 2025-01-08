/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 *
 * @author sbrandt
 */
public class Pool {
    private static Executor POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        var t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    });

    public static void setPool(Executor newPool) {
        POOL = newPool;
    }

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static Executor getExecutor() {
        return POOL;
    }

    /*public static <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> supplier) {
        return CompletableFuture.completedFuture(null)
                                .thenComposeAsync(x -> supplier.get(), POOL);
    }*/
}
