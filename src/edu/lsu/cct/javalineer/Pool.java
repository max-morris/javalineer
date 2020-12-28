/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 *
 * @author sbrandt
 */
public class Pool {
    private static final MyPool POOL = new MyPool(Integer.parseInt(System.getProperty("POOL_SIZE","4")));
    static {
        System.out.println("Pool size: "+POOL);
    }
    public static void await() {
        POOL.awaitQuiet();
    }
    public static void run(Runnable r) {
        POOL.add(r);
    }
    public static boolean runOne() {
        return POOL.runOne();
    }
    public static int busy() {
        return POOL.busy;
    }

    public static Executor getExecutor() {
        return POOL;
    }

    public static <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> supplier) {
        return POOL.supply(supplier);
    }
}
