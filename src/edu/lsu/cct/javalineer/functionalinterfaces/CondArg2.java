package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.concurrent.CompletableFuture;

public interface CondArg2<T1,T2> {
    void run(Var<T1> v1, Var<T2> v2, CompletableFuture<Boolean> fb);
}
