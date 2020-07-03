/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.javalineer;

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
    public Future(final Callable<T> t) {
        final Future<T> self = this;
        Pool.run(()->{
            try {
                self.set(t.call());
            } catch(Exception e) {
                self.setEx(e);
            }
        });
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
        boolean success = false;
        while(true) {
            next = pending.get();
            assert next != DONE;
            if(pending.compareAndSet(next,DONE)) {
                success = true;
                break;
            }
        }
        assert success;
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
        // TODO: Fix this
        if (data instanceof Future) {
            assert false;
            /*
            Future<T> f = (Future<T>)data;
            f.then(()->{
                self.set(f.get());
            });
            */
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


    public boolean finished() {
        return pending.get() == DONE;
    }

    public <R> Future<R> then(final Function<Val<T>,R> c) {
        final Future<R> f = new Future<>();
        Runnable r = ()->{
            try {
                f.set(c.apply(get_()));
            } catch(Exception e) {
                f.setEx(e);
            }
        };
        then(r);
        return f;
    }

    /**
     * This version of then is a function that returns a Future.
     * The Future of the return value doesn't complete until the
     * return value of the function supplied as an argument completes.
     */
    public <R> Future<R> thenFuture(final Function<Val<T>,Future<R>> c) {
        final Future<R> f = new Future<>();
        Runnable r = ()->{
            final Future<R> fc = c.apply(get_());
            fc.then(()->{
                try {
                    f.set(fc.get_().get());
                } catch(Exception e) {
                    f.setEx(e);
                }
            });
        };
        then(r);
        return f;
    }

    public static <T,R> Future<R> then(Future<T> f,final Function<Val<T>,R> c) {
        return f.then(c);
    }

    public static <T1,T2,R> Future<R> then(
            final Future<T1> f1,final Future<T2> f2,
            Then2<Val<T1>,Val<T2>,R> c) {
        final Future<R> f = new Future<>();
        final Runnable r1 = ()->{
            try {
                f.set(c.apply(f1.get_(),f2.get_()));
            } catch(Exception e) {
                f.setEx(e);
            }
        };
        Runnable r2 = ()->{
            f1.then(r1);
        };
        f2.then(r2);
        return f;
    }

    public static <T1,T2,T3,R> Future<R> then(
            final Future<T1> f1,final Future<T2> f2,final Future<T3> f3,
            Then3<Val<T1>,Val<T2>,Val<T3>,R> c) {
        final Future<R> f = new Future<>();
        final Runnable r1 = ()->{
            try {
                f.set(c.apply(f1.get_(),f2.get_(),f3.get_()));
            } catch(Exception e) {
                f.setEx(e);
            }
        };
        Runnable r2 = ()->{
            f1.then(r1);
        };
        Runnable r3 = ()->{
            f2.then(r2);
        };
        f3.then(r3);
        return f;
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

    /**
     * Unsafe method, used for demonstration purposes
     */
    @Deprecated
    public T get() {
        while(pending.get() != DONE)
           Pool.runOne();
        return get_().get();
    }

    private Val<T> get_() {
        assert pending.get() == DONE;
        return new Val<T>(data,ex);
    }

    public void then(Consumer<Val<T>> c) {
        Runnable r = ()->{ c.accept(this.get_()); };
        then(r);
    }

    @Override
    public String toString() {
        return "Future [" + (data == null ? "Not completed" : String.valueOf(data)) + "]";
    }
}
