package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Loop;
import edu.lsu.cct.javalineer.PartitionableList;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Heat {
    public static void main(String[] args) throws FileNotFoundException {
        Test.requireAssert();

        final int N_THREADS = 2;
        final int N_ITER = 160000;
        final double DIFFUSION_CONSTANT = 0.01;
        final int N = 1000;
        final double xmin = 0, xmax = 1;
        final double dx = (xmax - xmin) / N;
        final double dt = dx * dx / DIFFUSION_CONSTANT * 0.499;

        final List<PartitionableList<Double>> y = new ArrayList<>(2);
        y.add(PartitionableList.of(N, (i) -> {
            double x = (i - N / 2) * dx;
            return 10 * Math.exp(-x * x / 2);
        }));
        y.add(PartitionableList.of(N, (i) -> 0.0));

        Loop.marchingFor(0, N_ITER, (timeStep) -> {
            CompletableFuture<Void> fut = new CompletableFuture<>();
            PartitionableList.runPartitioned(N_THREADS, 1, y.get(0).read(), y.get(1).write(), (y0_, y1_) -> {
                final double c = dt*DIFFUSION_CONSTANT/dx/dx;
                for (int i = 0; i < y1_.writableSize(); i++) {
                    y1_.set(i, y0_.get(i) + c * (y0_.get(i + 1) + y0_.get(i - 1) - 2 * y0_.get(i)));
                }

            }).thenAccept((x1)->{
                PartitionableList.runPartitioned(1, y.get(1).readWrite(), (y1_)->{
                    y1_.set(0, y1_.get(y1_.readableSize()-2));
                    y1_.set(y1_.readableSize()-1,y1_.get(1));

                    if(timeStep%(2*N_ITER/10)==0) {
                        try(PrintWriter pw = new PrintWriter("plot"+timeStep+".xg")) {
                            for (int i = 0; i < y1_.writableSize(); i++) {
                                pw.println(i + " " + y1_.get(i));
                            }
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                    }
                }).thenAccept((x) -> {
                    var tmp = y.get(0);
                    y.set(0, y.get(1));
                    y.set(1, tmp);
                    fut.complete(null);
                    System.out.println("time step " + timeStep);
                });
            });
            return fut;
        }).join();
    }
}
