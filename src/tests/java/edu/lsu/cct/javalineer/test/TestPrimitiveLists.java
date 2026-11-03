package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PartitionableList.ofDouble and ofInt store their elements in a double[] or int[] (DoubleList, IntList). The views'
 * getDouble, setDouble, getInt and setInt read and write that storage, check the same bounds as get and set (ghosts
 * readable, only the part's own cells writable), and also work on a list of boxed elements; get and set work on a
 * primitive list as on any other.
 */
public class TestPrimitiveLists {
    static final long TIMEOUT_SECONDS = 30;
    static int checks = 0;

    static void check(boolean b, String what) {
        checks++;
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static boolean throwsOutOfBounds(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IndexOutOfBoundsException e) {
            return true;
        }
    }

    // A three-point average with one ghost cell, read through getDouble and written through setDouble.
    static void smooth(PartitionableList<Double> src, PartitionableList<Double> dst) throws Exception {
        PartitionableList.runPartitioned(4, 1, src.read(), dst.write(), (s, d) -> {
            for (int i = 0; i < d.writableSize(); i++) {
                d.setDouble(i, (s.getDouble(i - 1) + s.getDouble(i) + s.getDouble(i + 1)) / 3.0);
            }
            check(throwsOutOfBounds(() -> s.getDouble(-2)), "a read below the ghost cells is out of bounds");
            check(throwsOutOfBounds(() -> s.getDouble(d.writableSize() + 1)), "a read above them is too");
            check(throwsOutOfBounds(() -> d.setDouble(-1, 0.0)), "a write to a ghost cell is out of bounds");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        int n = 102;

        // Primitive and boxed storage give the same results through the primitive accessors
        var prim = PartitionableList.ofDouble(n, i -> (double) (i * i % 17));
        var primOut = PartitionableList.ofDouble(n, i -> 0.0);
        var boxed = PartitionableList.of(n, i -> (double) (i * i % 17));
        var boxedOut = PartitionableList.of(n, i -> 0.0);
        smooth(prim, primOut);
        smooth(boxed, boxedOut);
        var primSum = primOut.reducePartitioned(3, v -> {
            double s = 0;
            for (int i = 0; i < v.readableSize(); i++) {
                s += v.get(i);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(s);
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        var boxedSum = boxedOut.reducePartitioned(3, v -> {
            double s = 0;
            for (int i = 0; i < v.readableSize(); i++) {
                s += v.getDouble(i);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(s);
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(primSum.equals(boxedSum), "the same sums, got " + primSum + " and " + boxedSum);
        check(primSum > 0, "a nonzero sum");

        // Ints: read and written in place through a ReadWrite view, boxed get and set on the same storage
        List<Integer> start = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            start.add(i);
        }
        var ints = PartitionableList.ofInt(start);
        PartitionableList.runPartitioned(5, ints.readWrite(), v -> {
            for (int i = 0; i < v.writableSize(); i++) {
                v.setInt(i, v.getInt(i) * 3);
            }
            check(throwsOutOfBounds(() -> v.setInt(v.writableSize(), 0)), "a write past the part is out of bounds");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableList.runPartitioned(1, ints.readWrite(), v -> {
            for (int i = 0; i < v.writableSize(); i++) {
                check(v.get(i) == 3 * i, "element " + i + " through the boxed get");
                v.set(i, v.get(i) + 1);
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PartitionableList.runPartitioned(2, ints.read(), v -> {
            for (int i = 0; i < v.readableSize(); i++) {
                check(v.getInt(i) == 3 * (v.sourceBegin() + i) + 1, "element through getInt");
            }
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(ints.size() == 40, "the size");

        System.out.println("TestPrimitiveLists passed (" + checks + " checks)");
        System.exit(0);
    }
}
