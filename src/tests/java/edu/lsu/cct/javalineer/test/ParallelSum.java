package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionableList;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelSum {
    public static void main(String[] args) {
        final var n = 100_000_000;
        final var rand = new Random();

        var addends = IntStream.range(0, n)
                               .mapToObj(i -> rand.nextInt(10))
                               .collect(Collectors.toList());

        var pAddends = new PartitionableList<>(addends);

        var serialTime = System.currentTimeMillis();
        int serialSum = 0;
        for (int i = 0; i < n; i++) {
            serialSum += addends.get(i);
        }
        serialTime = System.currentTimeMillis() - serialTime;

        var parallelTime = System.currentTimeMillis();
        int parallelSum = pAddends.reducePartitioned(8, lv -> {
            int sum = 0;
            for (int i = 0; i < lv.size(); i++) {
                sum += lv.get(i);
            }
            return CompletableFuture.completedFuture(sum);
        }).join();
        parallelTime = System.currentTimeMillis() - parallelTime;

        System.out.printf("serialSum=%d (%d ms)\nparallelSum=%d (%d ms)\n", serialSum, serialTime, parallelSum, parallelTime);

        if (serialSum != parallelSum) {
            System.err.println("results do not match!");
        }
    }
}
