package edu.lsu.cct.javalineer;

interface WriteView<T> extends View<T> {
    void set(int i, T t);
    void setUnchecked(int i, T t);
}
