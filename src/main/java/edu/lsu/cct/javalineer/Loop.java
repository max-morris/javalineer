package edu.lsu.cct.javalineer;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

public class Loop {

    /**
     * Runs body(i) on the pool for each i in [i0, iN).
     *
     * @return A future which completes once every future returned by body has completed. It completes exceptionally
     *         as soon as any call to body throws (or returns null), or any future returned by body completes
     *         exceptionally. The remaining iterations still run.
     */
    public static CompletableFuture<Void> parFor(int i0, int iN, Function<Integer, CompletableFuture<Void>> body) {
        var result = new CompletableFuture<Void>();
        var done = new CountdownLatch(iN - i0);

        done.getFut().whenComplete((v, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
            } else {
                result.complete(null);
            }
        });

        for (int i = i0; i < iN; i++) {
            final int i_ = i;
            Pool.run(() -> body.apply(i_)).whenComplete((v, t) -> {
                if (t != null) {
                    result.completeExceptionally(t);
                } else {
                    done.signal();
                }
            });
        }

        return result;
    }

    /**
     * Runs body(i) for each i in [i0, iN) in order, starting each iteration once the future returned by the previous
     * iteration completes.
     *
     * @return A future which completes once the last iteration's future completes. It completes exceptionally if
     *         any call to body throws (or returns null) or any future returned by body completes exceptionally, in
     *         which case no further iterations are started.
     */
    public static CompletableFuture<Void> marchingFor(int i0, int iN, Function<Integer, CompletableFuture<Void>> body) {
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);

        for (int i = i0; i < iN; i++) {
            final int i_ = i;
            cf = cf.thenCompose((dummy) -> Pool.callCapturing(() -> body.apply(i_)));
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
