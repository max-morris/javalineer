package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck1;

import java.util.concurrent.CompletableFuture;

public class Latch<T> {
    private final GuardVar<T> latch;
    private final CompletableFuture<?> fut;

    public Latch(T initial, CondCheck1<T> task) {
        latch = new GuardVar<>(initial);
        fut = Guard.runCondition(latch, task);
    }

    public final void signal() {
        latch.signal();
    }

    public final CompletableFuture<?> getFut() {
        return fut;
    }
}
