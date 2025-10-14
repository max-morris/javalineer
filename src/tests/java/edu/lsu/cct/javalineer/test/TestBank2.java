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
        GuardVar<Bank> a = new GuardVar<>(new Bank());
        var bankNotEmpty = Guard.newCondition(a);
        CompletableFuture<Integer> done = new CompletableFuture<>();

        Loop.parForEach(0,1000,(i)->{
            final CompletableFuture<Void> withdrawFut = new CompletableFuture<>();
            Pool.run(() -> {
                Guard.runCondition(bankNotEmpty, bank -> {
                    if (bank.get().withdraw(1)) {
                        withdrawFut.complete(null);
                        return true;
                    } else {
                        return false;
                    }
                });
            });
            final CompletableFuture<Void> depositFut = new CompletableFuture<>();
            Pool.run(() -> {
                Guard.runGuarded(a, bank -> {
                    bank.get().deposit(1);
                    bankNotEmpty.signal();
                    depositFut.complete(null);
                });
            });
            return CompletableFuture.allOf(withdrawFut, depositFut);
        }).thenRun(()->{
            a.runGuarded((bank) -> {
                done.complete(bank.get().balance);
            });
        });
        assert done.join() == 0;
    }
}
