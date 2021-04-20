package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.Pool;

import java.util.TreeSet;

public class TestRunNow {
    public static void main(String[] args) throws InterruptedException {
        Guard g1 = new Guard();
        Guard g2 = new Guard();

        Guard.runGuarded(new TreeSet<>() {{ add(g1); }}, () -> {
            System.out.println("with g1 .. normal (long task)");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("long task ended");
        });

        //Guard.nowOrNever(new TreeSet<>() {{ add(g2); }}, () -> {
        //    System.out.println("with g2 .. nowOrNever");
        //});

        Guard.nowOrNever(new TreeSet<>() {{ add(g1); }}, () -> {
            System.out.println("with g1 .. immediately");
        });

        //Guard.runGuarded(g1, () -> {
        //    System.out.println("with g1 .. normal");
        //});

        Guard.runGuarded(g1, () -> {
            System.out.println("with g1 .. normal");
        });


        //Guard.nowOrNever(new TreeSet<>() {{ add(g2); }}, () -> {
        //    System.out.println("with g2 .. nowOrNever");
        //});

        Pool.await();
    }
}
