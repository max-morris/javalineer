package edu.lsu.cct.javalineer;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class MyPool implements Executor {
    volatile int busy = 0;

    synchronized void incrBusy() {
        busy++;
    }

    synchronized void decrBusy() {
        busy--;
        assert busy >= 0;
        if (busy == 0)
            notifyAll();
    }

    public boolean awaitQuiescence(long lg, java.util.concurrent.TimeUnit tu) {
        awaitQuiet();
        return true;
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

        Worker(final int id) {
            this.id = id;
            incrBusy();
            ll.add(()->{ me.set(id); });
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

        synchronized Runnable rmTaskWait() {
            while (ll.size() == 0) {
                try {
                    wait();
                } catch (InterruptedException ioe) {
                }
            }
            return rmTask();
        }
        synchronized Runnable rmTask() {
            if(ll.size() == 0)
                return null;
            Runnable gt = ll.removeFirst();
            if(ll.size() == 0) {
                return ()->{
                    try {
                        gt.run();
                    } finally {
                        decrBusy();
                    }
                };
            } else {
                return gt;
            }
        }

        public void run() {
            try {
                while (true) {
                    Runnable gt = rmTaskWait();
                    gt.run();
                }
            } catch (/*Throwable*/ Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        }

        public boolean runOne() {
            Runnable run = rmTask();
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

    public int getParallelism() { return size; }

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

    public boolean runOne() {
        assert size == workers.length;

        Integer meId = me.get();
        // We might not be on a worker thread...
        if(meId == null) 
            meId = RAND.nextInt(workers.length);

        for(int i=0;i<size;i++) {
            int id = (meId+i) % size;
            if(workers[id].runOne())
                return true;
        }
        return false;
    }

    @Override
    public void execute(Runnable command) {
        add(command);
    }

    public <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> supplier) {
        return CompletableFuture.completedFuture(null)
                                .thenComposeAsync(x -> supplier.get(), this);
    }

    public String toString() {
        return "MyPool("+size+")";
    }
}
