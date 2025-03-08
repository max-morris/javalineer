package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import edu.lsu.cct.javalineer.functionalinterfaces.CondTask1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Semaphore {
    Supplier<CompletableFuture<Void>> criticalSection;
    GuardVar<Integer> gCounter;
    CondContext<CondTask1<Integer>> counterNonZero;

    public Semaphore(int count, Supplier<CompletableFuture<Void>> criticalSection) {
        this.criticalSection = criticalSection;
        this.gCounter = new GuardVar<>(count);
        this.counterNonZero = Guard.newCondition(gCounter);
    }

    void dispatchCriticalSection() {
        Guard.runCondition(counterNonZero, counter -> {
            if (counter.get() == 0) {
                return false;
            }

            counter.set(counter.get() - 1);

            criticalSection.get().thenAccept(x -> {
                Guard.runGuarded(gCounter, counter1 -> {
                    counter1.set(counter1.get() + 1);
                    counterNonZero.signal();
                });
            });

            return true;
        });
    }

    public static void main(String[] args) {
        int maxCount = 4, threadCount = 64;
        var threadsInCriticalSection = new AtomicInteger(0);

        var done = new CountdownLatch(threadCount);

        var semaphore = new Semaphore(maxCount, () -> {
            var cf = new CompletableFuture<Void>();

            Pool.run(() -> {
                var tics = threadsInCriticalSection.incrementAndGet();
                if (tics > maxCount) {
                    System.err.println("!!! Too many threads in critical section: " + tics);
                } else {
                    System.out.printf("Entering critical section (%d -> %d)%n", tics - 1, tics);
                }

                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(100, 1000));
                } catch (InterruptedException ignored) { }

                tics = threadsInCriticalSection.decrementAndGet();
                System.out.printf("Leaving critical section (%d -> %d)%n", tics + 1, tics);

                done.signal();
                cf.complete(null);
            });

            return cf;
        });

        for (int i = 0; i < 128; i++) {
            semaphore.dispatchCriticalSection();
        }

        done.join();
    }
}