package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.concurrent.CompletableFuture;

public interface CondArg1<T> {
    void run(Var<T> v, CompletableFuture<Boolean> fb);
}
