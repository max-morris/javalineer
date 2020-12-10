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
        done();
    }
    public Future(Val<T> v) {
        data = v.getNoEx();
        ex = v.getEx();
        done();
    }
    public Future(final Callable<T> t) {
        final Future<T> self = this;
        Runnable r = ()->{
            try {
                self.set(t.call());
            } catch(Exception e) {
                self.setEx(e);
            }
        };
        Pool.run(r);
    }

    public static <T> Future<T> completedFuture(T t) {
        return new Future<>(t);
    }
    public static <T> Future<T> applyAsync(Callable<T> c) {
        return new Future<>(c);
    }
    public static <T> Future<T> applyAsyncFuture(Callable<Future<T>> c) {
        final Future<T> res = new Future<>();
        Runnable r2 = ()->{
            try {
                final Future<T> f = c.call();
                Runnable r = ()->{
                    res.setv(f.getv());
                };
                f.then(r);
            } catch(Exception e) {
                res.setEx(e);
            }
        };
        Pool.run(r2);
        return res;
    }
    public static <T> Future<T> apply(Callable<T> t) {
        Future<T> f = new Future<>();
        try {
            f.set(t.call());
        } catch(Exception e) {
            f.setEx(e);
        }
        return f;
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

    private void setv(Val<T> v) {
        if(v.getEx() != null)
            setEx(v.getEx());
        else
            set(v.getNoEx());
    }

    /**
     * Call to set a data value.
     */
    @SuppressWarnings("unchecked")
    private void set(final T data) {
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
                f.set(c.apply(getv()));
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
            final Future<R> fc = c.apply(getv());
            fc.then(()->{
                f.setv(fc.getv());
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
                f.set(c.apply(f1.getv(),f2.getv()));
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
                f.set(c.apply(f1.getv(),f2.getv(),f3.getv()));
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
        return getv().get();
    }

    Val<T> getv() {
        assert pending.get() == DONE;
        return new Val<T>(data,ex);
    }

    public void then(Consumer<Val<T>> c) {
        Runnable r = ()->{ c.accept(this.getv()); };
        then(r);
    }

    @Override
    public String toString() {
        return "Future [" + (data == null ? "Not completed" : String.valueOf(data)) + "]";
    }
}
