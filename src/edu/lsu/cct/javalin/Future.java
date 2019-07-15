/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalin;

import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author sbrandt
 */
public class Future<T> {
    static class FTask {
        Runnable task;
        FTask next;
        public String toString() {
            return "@"+next;
        }
    }

    private volatile T data = null;
    private volatile Throwable ex = null;
    private final static FTask DONE = new FTask();
    private final AtomicReference<FTask> pending = new AtomicReference<>(null);

    public Future() {}
    public Future(T t) {
        data = t;
        pending.set(DONE);
    }

    /**
     * Diagnostic.
     */
    private static boolean complainOnException;

    static {
        complainOnException = System.getProperty("FutExceptionComplain") != null;
        if(complainOnException)
            System.err.println("Fut exception complaining enabled");
    }

    private static void complain(Throwable e) {
        if (!complainOnException) return;
        System.err.println("Future completed exceptionally:");
        e.printStackTrace();
    }

    void done() {
        FTask next = null;
        while(true) {
            next = pending.get();
            assert next != DONE;
            if(pending.compareAndSet(next,DONE))
                break;
        }
        while(next != null) {
            final Runnable task = next.task;
            Pool.run(()->{
                task.run();
            });
            next = next.next;
        }
    }

    /**
     * Call to set a data value.
     */
    @SuppressWarnings("unchecked")
    public void set(final T data) {
        final Future<T> self = this;
        if (data instanceof Future) {
            Future<T> f = (Future<T>)data;
            f.then(()->{
                self.set(f.get());
            });
        } else {
            this.data = data;
            done();
        }
    }

    /**
     * Call if an exception was thrown.
     */
    public void setEx(Throwable ex) {
        complain(ex);
        this.data = null;
        this.ex = ex;
        done();
    }

    public T get() { 
        assert pending.get() == DONE;
        if(ex != null)
            throw new RuntimeException(ex);
        return data;
    }

    public void then(final Runnable r) {
        FTask ft = new FTask();
        ft.task = r;
        while(true) {
            ft.next = pending.get();
            if(ft.next == DONE) {
                Pool.run(()->{ r.run(); });
                break;
            } else if(pending.compareAndSet(ft.next, ft)) {
                break;
            }
        }
    }

    public void then(Consumer<Future<T>> c) {
        final Future<T> self = this;
        Runnable r = ()->{ c.accept(this); };
        then(r);
    }

    @Override
    public String toString() {
        return "Future [" + (data == null ? "Not completed" : String.valueOf(data)) + "]";
    }
}
