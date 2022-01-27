package edu.lsu.cct.javalineer;

public class Var<T> implements Get<T>, Set<T> {
    final Guard g;
    T data;

    Var(T t,Guard g) { data = t; this.g = g; }

    public void set(T t) {
        assert Guard.has(g) : "The current thread does not have access to this Guard.";
        data = t;
    }

    public T get() {
        assert Guard.has(g) : "The current thread does not have access to this Guard.";
        return data;
    }

    public void signal() {
        g.signal();
    }

    public void signalAll() {
        g.signalAll();
    }

    @SuppressWarnings("unchecked")
    public GuardVar<T> guardVar() {
        return (GuardVar)g;
    }
}
