package edu.lsu.cct.javalineer;

import java.util.Optional;

public interface OptionalGuardTask4<T1, T2, T3, T4> {
    void run(Optional<Var<T1>> o1, Optional<Var<T2>> o2, Optional<Var<T3>> o3, Optional<Var<T4>> o4);
}