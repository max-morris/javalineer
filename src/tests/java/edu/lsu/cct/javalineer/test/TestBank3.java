package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBank3 {
    static AtomicInteger wc = new AtomicInteger(0), tc = new AtomicInteger(0), dc = new AtomicInteger(0);

    static class Bank extends Guarded {
        int balance = 0;

        boolean withdraw(int a) {
            assert a > 0;
            if (a > balance)
                return false;
            balance -= a;
            return true;
        }

        void deposit(int a) {
            assert a > 0;
            balance += a;
        }
    }

    public static void main(String[] args) {
        Test.requireAssert();

        var doneLatch = new CountdownLatch(3000);

        GuardVar<Bank> a = new GuardVar<>(new Bank());
        GuardVar<Bank> b = new GuardVar<>(new Bank());

        var aNotEmpty = Guard.newCondition(a);
        var bNotEmpty = Guard.newCondition(a, b);

        for (int i = 0; i < 1000; i++) {
            Pool.run(() -> {
                Guard.runCondition(aNotEmpty, (Var<Bank> bankA) -> {
                    boolean b2 = bankA.get().withdraw(1);
                    if (b2) {
                        wc.getAndIncrement();
                        doneLatch.signal();
                    }
                    return b2;
                });
            });
            Pool.run(() -> {
                Guard.runCondition(bNotEmpty, (Var<Bank> bankA, Var<Bank> bankB) -> {
                    if (bankB.get().withdraw(1)) {
                        bankA.get().deposit(1);
                        aNotEmpty.signal();
                        tc.getAndIncrement();
                        doneLatch.signal();
                        return true;
                    } else {
                        return false;
                    }
                });
            });
            Pool.run(() -> {
                Guard.runGuarded(b, (Var<Bank> bankB) -> {
                    bankB.get().deposit(1);
                    bNotEmpty.signal();
                    dc.getAndIncrement();
                    doneLatch.signal();
                });
            });
        }

        doneLatch.join();

        int[] out = new int[1];
        System.out.println("wc=" + wc + ", tc=" + tc + ", dc=" + dc);

        var done = new CompletableFuture<Void>();
        a.runGuarded((bank) -> {
            out[0] = bank.get().balance;
            assert out[0] == 0 : out[0];
            done.complete(null);
        });

        done.join();
    }
}
