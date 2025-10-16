package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Loop;
import edu.lsu.cct.javalineer.ReadWriteGuard;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class TestReadWriteGuard {
    final static int LOOP_COUNT = 1000;
    final static int WRITE_EVERY = 10;
    final static int SLEEP_TIME = 2;
    final static int SLEEP_RAND = 10;
    // Testing
    public static void artificialWork() {
        try {
            Thread.sleep(SLEEP_TIME * ThreadLocalRandom.current().nextInt(SLEEP_RAND));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        assert (LOOP_COUNT % WRITE_EVERY) == 0;
        Test.requireAssert();
        final AtomicInteger readCounter = new AtomicInteger(0);
        final int[] writeCounter = {0};
        ReadWriteGuard g = new ReadWriteGuard();
        Loop.parFor(0, LOOP_COUNT, (j)->{
            artificialWork();
            CompletableFuture<Void> cf = new CompletableFuture<>();
            if (j % WRITE_EVERY == 0) {
                g.runWriteGuarded(() -> {
                    System.out.println("write " + j);
                    artificialWork();
                    writeCounter[0]++;
                    cf.complete(null);
                });
            } else {
                g.runReadGuarded(() -> {
                    System.out.println("read " + j);
                    artificialWork();
                    readCounter.incrementAndGet();
                    cf.complete(null);
                });
            }
            return cf;
        }).join();
        assert writeCounter[0] == LOOP_COUNT / WRITE_EVERY;
        assert readCounter.get() == LOOP_COUNT - writeCounter[0];
    }
}
