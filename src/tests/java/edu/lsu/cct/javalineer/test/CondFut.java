package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.concurrent.CompletableFuture;

public class CondFut {
    public static void main(String[] args) {
        Test.requireAssert();

        var done = new CompletableFuture<>();
        var counter = new GuardVar<>(0); // 0
        var cond = Guard.newCondition(counter);

        var f = Guard.runCondition(cond, (v) -> {
            v.set(v.get() + 1);
            return v.get() == 2;
        }); // 1

        cond.signal(); // 2

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
