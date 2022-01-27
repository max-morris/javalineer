package edu.lsu.cct.javalineer;

import java.util.Optional;

public interface OptionalGuardTask1<T> {
    void run(Optional<Var<T>> o);
}
