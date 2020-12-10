package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import java.util.function.Function;

public class TestFib {
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
        Future<Integer> f1 = Future.applyAsyncFuture(()->{ return fib(n-1); });
        Future<Integer> f2 = fib(n-2);
        /*
        return Future.then(f1, f2, (v1, v2)-> {
            return v1.get() + v2.get();
        });
        */
        return f1.thenFuture((final Val<Integer> v1)->{
            return f2.then((final Val<Integer> v2)->{
                return v1.get()+v2.get();
            });
        });
    }

    public static void main(String[] args) {
        Test.requireAssert();

        for(int i = 5; i < 40; i++) {
            final int f = i;
            Future<Integer> fib = fib(f);
            fib.then((n)->{
                System.out.printf("fib(%d)=%d%n",f,n.get());
                assert n.get() == fibc(f);
            });
        }

        Pool.await();
    }
}
