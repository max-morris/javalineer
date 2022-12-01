package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.Pool;

import java.util.TreeSet;

public class TestRunNow {
    public static void main(String[] args) throws InterruptedException {/*
        Guard g1 = new Guard();
        Guard g2 = new Guard();

        Guard.nowOrNever(g1, () -> {
            System.out.println("with g1 .. nowOrNever first");
        });

        Guard.runGuarded(g1, () -> {
            System.out.println("with g1 .. normal (long task)");
            try {
                for(int i = 0; i < 10; i++) {
                    Thread.sleep(1000);
                    if (!Guard.has(g1)) {
                        System.out.println("bad");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("long task ended");
        });

        Guard.nowOrNever(g2, () -> {
            System.out.println("with g2 .. nowOrNever");
        });

        Guard.nowOrElse(g1, () -> {
            System.out.println("error");
        }, () -> {
            assert !Guard.has(g1);
            System.out.println("with g1 .. norOrElse, ran fallback");
        });

        Guard.runGuarded(g1, () -> {
            System.out.println("with g1 .. normal");
        });

        Guard.runGuarded(g1, () -> {
            System.out.println("with g1 .. normal");
        });

        Guard.nowOrNever(g2, () -> {
            System.out.println("with g2 .. nowOrNever");
        });

        Pool.await();*/
    }
}
