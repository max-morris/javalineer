package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CondArgN {
    void run(List<Var<Object>> v, CompletableFuture<Boolean> fb);
}
