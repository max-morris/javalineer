/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author sbrandt
 */
public class Pool {
    private static final ForkJoinPool POOL = new ForkJoinPool(
            Integer.parseInt(System.getProperty("POOL_SIZE", "4"))
    );

    static {
        System.out.println("Pool size: " + POOL.getParallelism());
    }

    public static void await() {
        //noinspection StatementWithEmptyBody
        while (!await(5, TimeUnit.MINUTES)); // todo Probably should do something different
    }

    public static boolean await(long timeout, TimeUnit unit) {
        return POOL.awaitQuiescence(timeout, unit);
    }

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static Executor getExecutor() {
        return POOL;
    }

    public static <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> supplier) {
        return CompletableFuture.completedFuture(null)
                                .thenComposeAsync(x -> supplier.get(), POOL);
    }
}
