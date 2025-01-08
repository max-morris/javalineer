package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;

public abstract class CondTask implements Runnable {
    protected volatile boolean done = false;
    protected final CompletableFuture<Void> fut = new CompletableFuture<>();

    public abstract void run();
    
    public final boolean isDone() { return done; }
}
