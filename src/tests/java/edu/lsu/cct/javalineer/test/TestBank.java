package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestBank {

    static class Bank {
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

    static int failures = 0;

    public static void main(String[] args) {
        Test.requireAssert();

        GuardVar<Bank> a = new GuardVar<>(new Bank());
        CompletableFuture<Integer> done = new CompletableFuture<>();

        Loop.parForEach(0,100,(i)->{
            final CompletableFuture<Void> withdrawFut = new CompletableFuture<>();
            Pool.run(() -> {
                a.runGuarded((bank) -> {
                    if (!bank.get().withdraw(1))
                        failures++;
                    withdrawFut.complete(null);
                });
            });
            final CompletableFuture<Void> depositFut = new CompletableFuture<>();
            Pool.run(() -> {
                a.runGuarded((bank) -> {
                    bank.get().deposit(1);
                    depositFut.complete(null);
                });
            });
            return CompletableFuture.allOf(withdrawFut, depositFut);
        }).thenRun(() -> {
            a.runGuarded((bank) -> {
                int balance = -1;
                try {
                    balance = bank.get().balance;
                    assert balance == failures;
                } finally {
                    done.complete(balance);
                }
            });
        });
        System.out.println("failures = "+done.join());
    }
}
