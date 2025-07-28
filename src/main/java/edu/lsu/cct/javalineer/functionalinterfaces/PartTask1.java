package edu.lsu.cct.javalineer.functionalinterfaces;

@FunctionalInterface
public interface PartTask1<T1, R> {
    R apply(T1 t1);
}
