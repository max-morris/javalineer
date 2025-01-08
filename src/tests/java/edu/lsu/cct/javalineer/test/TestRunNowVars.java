package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

public class TestRunNowVars {
    public static void main(String[] args) {/*
        var g1 = new GuardVar<>("g1's data");
        var g2 = new GuardVar<>("g2's data");

        Guard.runGuarded(g1, v -> {
            System.out.println("Doing something slow with g1 ...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) { }
            System.out.println("Done with g1");
        });

        Guard.now(g1, g2, (o1, o2) -> {
            System.out.println(o1);
            System.out.println(o2);
        });

        Guard.runGuarded(g1, g2, (v1, v2) -> {
            System.out.println(v1);
            System.out.println(v2);
        });

        Pool.await();*/
    }
}
