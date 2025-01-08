package edu.lsu.cct.javalineer.test;

public class Test {
    public static void requireAssert() {
        try {
            assert false;
            throw new Error("Please enable assertions.");
        } catch(AssertionError ae) {
        }
    }
}
