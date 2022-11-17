package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomRef<T> {
    final static AtomicInteger nextId = new AtomicInteger(0);
    final int id = nextId.getAndIncrement();
    private T data;
    private int count = 0;

    public synchronized T getAndSet(T t) {
        T tmp = data;
        data = t;
        return tmp;
    }

    public synchronized boolean compareAndSet(T oldVal, T newVal) {
        assert count != 0 || data == null;
        assert count != 1 || data != null;

        count++;

        assert count <= 2 : "Only 2 violated";

        if (data == oldVal) {
            data = newVal;
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        return "AtomRef(" + id + "," + (null == data) + ")";
    }

    public synchronized T get() {
        return data;
    }
}
