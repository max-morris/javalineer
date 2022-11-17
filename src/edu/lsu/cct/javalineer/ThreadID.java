package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadID {
    final static AtomicInteger nextId = new AtomicInteger(0);
    final static ThreadLocal<Integer> id = new ThreadLocal<>() {
        public Integer initialValue() {
            return nextId.getAndIncrement();
        }
    };
    public static int get() {
        return id.get();
    }
}
