package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

public class DebugPool implements Executor {

    String seed;

    public DebugPool() {
        seed = System.getProperty("JAVALINEER_DEBUG_POOL_SEED", null);

        // The seed is printed so that a run with a random seed can be reproduced.
        if (seed == null) {
            seed = "" + RANDOM.nextInt();
            System.out.println("JAVALINEER_DEBUG_POOL_SEED was unset; using random seed: " + seed);
        } else {
            System.out.println("Using JAVALINEER_DEBUG_POOL_SEED: " + seed);
        }
        System.out.flush();

        try {
            RANDOM.setSeed(Integer.parseInt(seed));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid JAVALINEER_DEBUG_POOL_SEED: '" + seed + "'", e);
        }
    }

    /**
     * Retrieves the seed which determines the order in which tasks run. If JAVALINEER_DEBUG_POOL_SEED was unset, this
     * is the randomly chosen seed; set JAVALINEER_DEBUG_POOL_SEED to it to reproduce a run.
     *
     * @return The seed.
     */
    public String getSeed() {
        return seed;
    }

    public String toString() {
        return "DebugPool(" + seed + ")";
    }

    List<Runnable> tasks = new ArrayList<>();

    final static Random RANDOM = new Random();

    public void run(Runnable command) {
        execute(command);
    }

    boolean running = false;

    @Override
    public void execute(Runnable command) {
        tasks.add(command);
        if (running) return;
        running = true;
        while (!tasks.isEmpty()) {
            int nextTask = RANDOM.nextInt(tasks.size());
            Runnable task = tasks.remove(nextTask);
            try {
                task.run();
            } catch (Throwable t) {
                Pool.handleUncaught(t);
            }
        }
        running = false;
    }
}
