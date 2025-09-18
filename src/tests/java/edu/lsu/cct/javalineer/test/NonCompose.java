package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.concurrent.CompletableFuture;

public class NonCompose {
    private final GuardVar<Integer> gA, gB;

    public NonCompose() {
        gA = new GuardVar<>(0);
        gB = new GuardVar<>(0);
    }

    public CompletableFuture<Void> incrA() {
        var fut = new CompletableFuture<Void>();

        Guard.runGuarded(gA, a -> {
            a.set(a.get() + 1);
            tooString().thenAccept(System.out::println).thenRun(() -> fut.complete(null));
        });

        return fut;
    }

    public CompletableFuture<Void> incrB() {
        var fut = new CompletableFuture<Void>();

        Guard.runGuarded(gB, b -> {
            b.set(b.get() + 1);
            tooString().thenAccept(System.out::println).thenRun(() -> fut.complete(null));
        });

        return fut;
    }

    public CompletableFuture<String> tooString() {
        var fut = new CompletableFuture<String>();

        Guard.runGuarded(gA, gB, (a, b) -> {
            fut.complete("a = " + a.get() + ", b = " + b.get());
        });

        return fut;
    }

    public static void main(String[] args) {
        var n = new NonCompose();
        n.incrB();n.incrA();n.incrA();n.incrB();n.incrA();n.incrB();n.incrA();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
