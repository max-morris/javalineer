package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CondLink {
    volatile AtomicReference<CondLink> next = new AtomicReference<>(null);
    final Cond cond;
    final Error err = new Error();
    public CondLink(Cond c) { cond = c; }
    public String toString() {
        return cond.toString()+"|"+next;
    }
}
