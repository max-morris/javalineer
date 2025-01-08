package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;

import java.util.*;

public class TestGuard {
    static int c1 = 0, c2 = 0;
    final static int N_INCR = 100;
    final static int N_TASK = 1000;

    static void incrC1() {
        for (int i = 0; i < N_INCR; i++)
            c1++;
    }

    static void incrC2() {
        for (int i = 0; i < N_INCR; i++)
            c2++;
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        Guard g1 = new Guard();
        Guard g2 = new Guard();
        TreeSet<Guard> ts = new TreeSet<>();
        ts.add(g1);
        ts.add(g2);

        var doneLatch = new CountdownLatch(N_TASK * 3);

        for (int i = 0; i < N_TASK; i++) {
            Guard.runGuarded(g1, () -> {
                incrC1();
                doneLatch.signal();
            });
            Guard.runGuarded(g2, () -> {
                incrC2();
                doneLatch.signal();
            });
            Guard.runGuarded(ts, () -> {
                incrC1();
                incrC2();
                doneLatch.signal();
            });
        }

        doneLatch.join();

        System.out.printf("c1=%d, c2=%d%n", c1, c2);
        assert 2 * N_TASK * N_INCR == c1;
        assert 2 * N_TASK * N_INCR == c2;
    }
}
