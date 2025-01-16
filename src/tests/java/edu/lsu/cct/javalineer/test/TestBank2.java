package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck1;
import edu.lsu.cct.javalineer.functionalinterfaces.GuardTask1;

import java.util.concurrent.CompletableFuture;

public class TestBank2 {

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

        var doneLatch = new CountdownLatch(2000);

        GuardVar<Bank> a = new GuardVar<>(new Bank());
        var bankNotEmpty = Guard.newCondition(a);

        for (int i = 0; i < 1000; i++) {
            Pool.run(() -> {
                Guard.runCondition(bankNotEmpty, bank -> {
                    if (bank.get().withdraw(1)) {
                        doneLatch.signal();
                        return true;
                    } else {
                        return false;
                    }
                });
            });
            Pool.run(() -> {
                Guard.runGuarded(a, bank -> {
                    bank.get().deposit(1);
                    bankNotEmpty.signal();
                    doneLatch.signal();
                });
            });
        }

        doneLatch.join();
        int[] out = new int[1];

        var done = new CompletableFuture<Void>();
        a.runGuarded((bank) -> {
            out[0] = bank.get().balance;
            assert out[0] == 0;
            done.complete(null);
        });

        done.join();
    }
}
