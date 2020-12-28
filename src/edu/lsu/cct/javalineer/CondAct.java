package edu.lsu.cct.javalineer;

import java.util.concurrent.CompletableFuture;

public interface CondAct {
    void act(CompletableFuture<Boolean> _result);
}
