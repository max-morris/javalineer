package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.Optional;

public interface OptionalGuardTask1<T> {
    void run(Optional<Var<T>> o);
}
