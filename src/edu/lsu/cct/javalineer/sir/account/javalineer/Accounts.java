package edu.lsu.cct.javalineer.sir.account.javalineer;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

import java.util.concurrent.CompletableFuture;

/**
 * Javalineer implementation of the `account` artifact from (https://sir.csc.ncsu.edu/content/bios/account.php).
 *
 * The original version deadlocks.
 * The Javalineer version does not deadlock and has no race conditions.
 */
public class Accounts {
    static class Account {
        private static int _id = 0;

        public final int id = _id++;
        public final GuardVar<Double> balance;

        public Account(double initialBalance) {
            this.balance = new GuardVar<>(initialBalance);
        }

        public void deposit(double amount) {
            Guard.runGuarded(balance, b -> b.set(b.get() + amount));
        }

        public void withdraw(double amount) {
            deposit(-amount);
        }

        public void transfer(Account destination, double amount) {
            Guard.runGuarded(balance, destination.balance, (myBalance, yourBalance) -> {
                myBalance.set(myBalance.get() - amount);
                yourBalance.set(yourBalance.get() + amount);
            });
        }

        @Override
        public String toString() {
            return String.format("Account(%d)", id);
        }

        public CompletableFuture<String> toStringAsync() {
            var ret = new CompletableFuture<String>();
            Guard.runGuarded(balance, (b) -> ret.complete(String.format("Account(%d): $%f", id, b.get())));
            return ret;
        }
    }

    private static final int N_ACCOUNTS = 10;

    public static void main(String[] args) {
        var accounts = new Account[N_ACCOUNTS];

        for (int i = 0; i < N_ACCOUNTS; i++) {
            accounts[i] = new Account(100);
        }

        for (int i = 0; i < N_ACCOUNTS; i++) {
            final int thisI = i;
            Pool.run(() -> {
                accounts[thisI].deposit(300);
                accounts[thisI].transfer(accounts[(thisI + 1) % N_ACCOUNTS], 10);
                accounts[thisI].deposit(10);
                accounts[thisI].transfer(accounts[(thisI + 2) % N_ACCOUNTS], 10);
                accounts[thisI].withdraw(20);
                accounts[thisI].deposit(10);
                accounts[thisI].transfer(accounts[(thisI + 1) % N_ACCOUNTS], 10);
                accounts[thisI].withdraw(100);
            });
        }

        Pool.await();

        for (int i = 0; i < N_ACCOUNTS; i++) {
            //accounts[i].toStringAsync().thenAccept(System.out::println);
            Guard.runGuarded(accounts[i].balance, (b) -> {
                assert b.get().equals(300.); // any account ending on != $300 indicates a race condition
            });
        }

        Pool.await();
    }
}
