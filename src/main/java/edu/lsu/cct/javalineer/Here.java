package edu.lsu.cct.javalineer;

public class Here {
    public static void println(Object out) {
        Throwable t = new Throwable();
        StackTraceElement[] elems = t.getStackTrace();
        System.out.println(elems[1]+": "+out);
    }
    public static void print(Object out) {
        Throwable t = new Throwable();
        StackTraceElement[] elems = t.getStackTrace();
        System.out.print(elems[1]+": "+out);
    }
}
