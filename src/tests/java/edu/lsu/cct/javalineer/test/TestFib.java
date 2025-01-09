package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

import java.util.concurrent.CompletableFuture;

public class TestFib {
    static int fibc(int n) {
        if (n < 2) return n;
        return fibc(n - 1) + fibc(n - 2);
    }

    static int fib_sync(int n) {
        if (n < 2)
            return n;
        else
            return fib_sync(n - 1) + fib_sync(n - 2);
    }

    static CompletableFuture<Integer> fib(int n) {
        if (n < 2)
            return CompletableFuture.completedFuture(n);
        if (n < 20)
            return CompletableFuture.completedFuture(fib_sync(n));

        CompletableFuture<Integer> f1 = CompletableFuture.completedFuture(n - 1)
                                                         .thenComposeAsync(TestFib::fib, Pool.getPool());
        CompletableFuture<Integer> f2 = fib(n - 2);

        return f1.thenCombine(f2, Integer::sum);
    }

    public static void main(String[] args) {
        Test.requireAssert();

        final int origin = 5, bound = 40;
        var doneLatch = new CountdownLatch(bound - origin);

        for (int i = origin; i < bound; i++) {
            final int f = i;
            fib(f).thenAccept(n -> {
                System.out.printf("fib(%d)=%d%n", f, n);
                assert n == fibc(f);
                doneLatch.signal();
            });
        }

        doneLatch.join();
    }
}
