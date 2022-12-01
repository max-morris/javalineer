
package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.Optional;

public interface OptionalGuardTask3<T1, T2, T3> {
    void run(Optional<Var<T1>> o1, Optional<Var<T2>> o2, Optional<Var<T3>> o3);
}

