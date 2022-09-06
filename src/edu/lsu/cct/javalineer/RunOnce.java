package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicInteger;

public class RunOnce implements Runnable {
    AtomicInteger ai = new AtomicInteger(0);
    final Runnable r;
    public RunOnce(Runnable r) {
        this.r = r;
    }
    public final void run() {
        if(ai.incrementAndGet() == 2) {
            Throwable t = new Throwable("Dup");
            t.printStackTrace();
            System.out.flush();
            System.exit(2);
        }
        this.r.run();
    }
}
