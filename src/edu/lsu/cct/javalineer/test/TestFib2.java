package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

public class TestFib2 {
    static int fibc(int n) {
        if(n < 2) return n;
        return fibc(n-1) + fibc(n-2);
    }

    static int fib_sync(int n) {
        if(n < 2)
            return n;
        else
            return fib_sync(n-1)+fib_sync(n-2);
    }

    static Future<Integer> fib(int n) {
        if(n < 2)
            return new Future<Integer>(n);
        if(n < 20)
            return new Future<Integer>(fib_sync(n));
        Future<Integer> f1 = Future
            .applyAsyncFuture(()->{ return fib(n-1); });
        Future<Integer> f2 = fib(n-2);
        return new Future<Integer>(f1.get() + f2.get());
    }

    public static void main(String[] args) {
        Test.requireAssert();

        for(int i = 5; i < 30; i++) {
            final int f = i;
            Future<Integer> fib = fib(f);
            fib.get();
            System.out.printf("fib(%d)=%d%n",f,fib.get());
            assert fib.get() == fibc(f);
        }

        Pool.await();
    }
}
