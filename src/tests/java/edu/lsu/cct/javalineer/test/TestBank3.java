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

        GuardVar<Bank> a = new GuardVar<>(new Bank());
        GuardVar<Bank> b = new GuardVar<>(new Bank());

        var aNotEmpty = Guard.newCondition(a);
        var bNotEmpty = Guard.newCondition(a, b);

        final CompletableFuture<Integer> done = new CompletableFuture<>();

        Loop.parForEach(0,1000,(i)->{
            final CompletableFuture<Void> withdrawFut = new CompletableFuture<>();
            Pool.run(() -> {
                Guard.runCondition(aNotEmpty, (Var<Bank> bankA) -> {
                    boolean b2 = bankA.get().withdraw(1);
                    if (b2) {
                        wc.getAndIncrement();
                        withdrawFut.complete(null);
                    }
                    return b2;
                });
            });
            final CompletableFuture<Void> transferFut = new CompletableFuture<>();
            Pool.run(() -> {
                Guard.runCondition(bNotEmpty, (Var<Bank> bankA, Var<Bank> bankB) -> {
                    if (bankB.get().withdraw(1)) {
                        bankA.get().deposit(1);
                        aNotEmpty.signal();
                        tc.getAndIncrement();
                        transferFut.complete(null);
                        return true;
                    } else {
                        return false;
                    }
                });
            });
            final CompletableFuture<Void> depositFut = new CompletableFuture<>();
            Pool.run(() -> {
                Guard.runGuarded(b, (Var<Bank> bankB) -> {
                    bankB.get().deposit(1);
                    bNotEmpty.signal();
                    dc.getAndIncrement();
                    depositFut.complete(null);
                });
            });
            return CompletableFuture.allOf(withdrawFut, depositFut, transferFut);
        }).thenRun(()->{
            a.runGuarded((bank) -> {
                done.complete(bank.get().balance);
            });
        });
        assert done.join() == 0;
        System.out.println("wc=" + wc + ", tc=" + tc + ", dc=" + dc);
        assert wc.get() == tc.get() && tc.get() == dc.get();
    }
}
