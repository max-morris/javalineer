package edu.lsu.cct.javalineer;

public class Val<T> {
    T data;
    Throwable ex;

    Val(T t,Throwable ex) { data = t; this.ex = ex; }

    public T get() {
        if(ex != null)
            throw new RuntimeException(ex);
        return data;
    }
    public T getNoEx() {
        return data;
    }
    public Throwable getEx() {
        return ex;
    }
    public String toString() {
        if(ex != null)
            return ex.toString();
        if(data != null)
            return data.toString();
        return "null";
    }
}
