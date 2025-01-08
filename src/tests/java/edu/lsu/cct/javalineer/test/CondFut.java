package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.concurrent.CompletableFuture;

public class CondFut {
    public static void main(String[] args) {
        Test.requireAssert();

        var done = new CompletableFuture<>();
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
                done.complete(null);
            });
        });

        done.join();
    }
}
