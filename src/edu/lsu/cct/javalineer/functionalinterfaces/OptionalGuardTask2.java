
package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.Optional;

public interface OptionalGuardTask2<T1, T2> {
    void run(Optional<Var<T1>> o1, Optional<Var<T2>> o2);
}

