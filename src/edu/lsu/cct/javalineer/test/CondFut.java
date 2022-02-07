package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

public class CondFut {
    public static void main(String[] args) {
        GuardVar<Integer> counter = new GuardVar<>(0); // 0

        var f = Guard.runCondition(counter, (v) -> {
            v.set(v.get() + 1);
            return v.get() == 2;
        }); // 1

        counter.signal(); // 2

        f.thenRun(() -> {
            counter.runGuarded((v) -> {
                System.out.println("2 == " + v.get());
                assert v.get() == 2;
            });
        });

        Pool.await();
    }
}
