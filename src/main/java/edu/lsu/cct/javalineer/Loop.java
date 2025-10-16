package edu.lsu.cct.javalineer;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

public class Loop {

    public static CompletableFuture<?> parFor(int i0, int iN, Function<Integer, CompletableFuture<Void>> body) {
        var done = new CountdownLatch(iN - i0);

        for (int i = i0; i < iN; i++) {
            final int i_ = i;
            Pool.run(() -> {
                body.apply(i_).thenRun(done::signal);
            });
        }

        return done.getFut();
    }

    public static CompletableFuture<Void> marchingFor(int i0, int iN, Function<Integer, CompletableFuture<Void>> body) {
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);

        for (int i = i0; i < iN; i++) {
            final int i_ = i;
            cf = cf.thenCompose((dummy) -> body.apply(i_));
        }

        return cf;
    }

    public static void main(String[] args) throws Exception {
        var f = marchingFor(0, 10, (i0) -> {
            final CompletableFuture<Void> cf = new CompletableFuture<>();
            Runnable r = () -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) { }
                System.out.println("tick " + i0);
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
