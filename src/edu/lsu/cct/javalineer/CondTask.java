package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;

public abstract class CondTask implements Runnable {
    volatile boolean done = false;
    final CompletableFuture<Void> fut = new CompletableFuture<>();

    public abstract void run();
    
    public final boolean isDone() { return done; }
}
