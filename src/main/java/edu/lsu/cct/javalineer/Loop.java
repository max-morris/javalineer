package edu.lsu.cct.javalineer;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

public class Loop {

    public static CompletableFuture<Void> parForEach(int i0, int iN, Function<Integer,CompletableFuture<Void>> body) {
        for(int i=i0; i<iN; i++) {
            final int i_ = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            Pool.submit(()->{
            });
        }
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);
        for(CompletableFuture<
    }

    public static CompletableFuture<Void> marchingForEach(int i0, int iN, Function<Integer,CompletableFuture<Void>> body) {
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);
        for(int i=i0; i<iN; i++) {
            final int i_ = i;
            cf = cf.thenCompose((dummy)->{ return body.apply(i_); });
        }
        return cf;
    }

    public static void main(String[] args) throws Exception {
        var f = marchingForEach(0,10,(i0)->{
            final CompletableFuture<Void> cf = new CompletableFuture<>();
            Runnable r = ()->{
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException ie) {}
                System.out.println("tick "+i0);
                cf.complete(null);
            };
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.start();
            return cf;
        });
        f.join();
    }
}
