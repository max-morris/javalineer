package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck1;
import edu.lsu.cct.javalineer.functionalinterfaces.CondTask1;

import java.util.concurrent.CompletableFuture;

public class Latch<T> {
    private final CondContext<CondTask1<T>> cond;
    private final CompletableFuture<?> fut;

    public Latch(T initial, CondCheck1<T> task) {
        cond = CondContext.newCond(new GuardVar<>(initial));
        fut = Guard.runCondition(cond, task);
    }

    public final void signal() {
        cond.signal();
    }

    public final CompletableFuture<?> getFut() {
        return fut;
    }

    public final void join() {
        fut.join();
    }
}
