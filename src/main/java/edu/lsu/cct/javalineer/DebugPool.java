package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

public class DebugPool implements Executor {

    String seed;

    public DebugPool() {
        seed = System.getProperty("JAVALINEER_DEBUG_POOL_SEED", null);

        if (seed == null) {
            seed = "" + RANDOM.nextInt();
            System.out.println("JAVALINEER_DEBUG_POOL_SEED was unset; using random seed: " + seed);
        } else {
            System.out.println("Using JAVALINEER_DEBUG_POOL_SEED: " + seed);
        }

        try {
            RANDOM.setSeed(Integer.parseInt(seed));
        } catch (NumberFormatException e) {
            System.err.println("Invalid JAVALINEER_DEBUG_POOL_SEED: '" + seed + "'");
            throw new RuntimeException(e);
        } finally {
            System.out.flush();
        }
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
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        running = false;
    }
}
