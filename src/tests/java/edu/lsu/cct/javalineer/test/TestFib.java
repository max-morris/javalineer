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

        CompletableFuture<Integer> f1 = Pool.run(() -> fib(n - 1));
        CompletableFuture<Integer> f2 = fib(n - 2);

        return f1.thenCombine(f2, Integer::sum);
    }

    public static void main(String[] args) {
        Test.requireAssert();

        final int origin = 5, bound = 40;
        Loop.marchingForEach(origin, bound,(n)->{
            return fib(n).thenAccept(n2 -> {
                System.out.printf("fib(%d)=%d%n", n, n2);
            });
        }).join();
    }
}
