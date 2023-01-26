package edu.lsu.cct.javalineer;

public class CountdownLatch extends Latch<Integer> {

    public CountdownLatch(int n) {
        // n + 1 because runCondition will immediately decrement by 1
        super(n + 1, counter -> {
            counter.set(counter.get() - 1);
            return counter.get() == 0;
        });
    }
}
