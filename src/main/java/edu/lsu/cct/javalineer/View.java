package edu.lsu.cct.javalineer;

public interface View<T> {
    int readableSize();
    int writableSize();
    int ghostSize();
    int partitionNum();
    int begin();
    int end();
}
