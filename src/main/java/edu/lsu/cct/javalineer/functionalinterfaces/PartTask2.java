package edu.lsu.cct.javalineer.functionalinterfaces;

@FunctionalInterface
public interface PartTask2<T1, T2, R> {
    R apply(T1 t1, T2 t2);
}
