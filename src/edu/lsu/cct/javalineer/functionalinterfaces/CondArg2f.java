package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.Var;

import java.util.List;

public interface CondArg2f<T1,T2> {
    boolean run(Var<T1> v1, Var<T2> v2);
}
