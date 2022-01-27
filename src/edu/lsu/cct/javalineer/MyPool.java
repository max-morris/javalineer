package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MyPool {
    int busy = 0;

    synchronized void incrBusy() {
        busy++;
    }

    synchronized void decrBusy() {
        busy--;
        assert busy >= 0;
        if (busy == 0)
            notifyAll();
    }

    synchronized void awaitQuiet() {
        while (busy > 0) {
            try {
                wait();
            } catch (InterruptedException ioe) {
            }
        }
        for(Worker w : workers) {
            assert w.ll.size()==0;
        }
    }

    final static ThreadLocal<Integer> me = new ThreadLocal<>();

    class Worker extends Thread {
        final int id;

        Worker(int id) {
            this.id = id;
            me.set(id);
        }

        LinkedList<Runnable> ll = new LinkedList<>();

        synchronized void addTask(Runnable t) {
            ll.addLast(t);
            // This worker just became busy
            if(ll.size() == 1) {
                incrBusy();
                // this worker now has tasks...
                notifyAll();
            }
        }

        synchronized Runnable rmTask(boolean[] done) {
            while (ll.size() == 0) {
                try {
                    wait();
                } catch (InterruptedException ioe) {
                }
            }
            Runnable gt = ll.removeFirst();
            done[0] = ll.size() == 0;
            return gt;
        }

        public void run() {
            boolean[] done = new boolean[1];
            try {
                while (true) {
                    Runnable gt = rmTask(done);
                    try {
                        gt.run();
                    } finally {
                        if(done[0])
                            decrBusy();
                    }
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        }

        private synchronized Runnable rmOne() {
            if(ll.size() == 0)
                return null;
            else
                return ll.removeFirst();
        }
        public boolean runOne() {
            Runnable run = rmOne();
            if(run != null) {
                run.run();
                return true;
            } else {
                return false;
            }
        }
    }

    final Random RAND = new Random();

    /**
     * Give task to a random worker.
     */
    void add(Runnable gt) {
        int n = RAND.nextInt(workers.length);
        workers[n].addTask(gt);
    }

    final int size;
    Worker[] workers;
    public MyPool(int size) {
        this.size = size;
        workers = new Worker[size];
        for (int i = 0; i < size; i++) {
            Worker w = this.new Worker(i);
            workers[i] = w;
            w.setDaemon(true);
            w.start();
        }
    }

    public void runOne() {
        for(int i=0;i<size;i++) {
            while(me.get() == null)
                Thread.yield();
            int id = (me.get()+i) % size;
            if(workers[id].runOne())
                return;
        }
    }

    public String toString() {
        return "MyPool("+size+")";
    }
}
