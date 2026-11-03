package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.PartitionRangeProvider;
import edu.lsu.cct.javalineer.PartitionableList;
import edu.lsu.cct.javalineer.ReadOnlyPartListView;
import edu.lsu.cct.javalineer.ReadWritePartListView;
import edu.lsu.cct.javalineer.WriteOnlyPartListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A slice of a part view is in the part's local indices, so it must start at the parent's offset plus the requested
 * offset, keep the parent's partition number, and lie within the parent's readable (and, for a writable view, its
 * writable) range. Before the fix a slice dropped the parent's offset, so in any part not starting at base index 0 it
 * reached into a lower part's range: a data race between the parts of one partitioned call.
 */
public class TestPartViewSlice {
    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static void expectOutOfRange(Runnable r, String what) {
        try {
            r.run();
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        throw new AssertionError("FAILED: no IndexOutOfBoundsException for " + what);
    }

    static List<Double> zeros(int n) {
        return new ArrayList<>(Collections.nCopies(n, 0.0));
    }

    /** Parts (0, 4) and (4, 8), as in the Guardian probe race2.gu4. */
    static final PartitionRangeProvider TWO_PARTS = new PartitionRangeProvider() {
        public int numPartitions() { return 2; }
        public int getWritableBegin(int p) { return 4 * p; }
        public int getWritableEnd(int p) { return 4 * p + 4; }
    };

    /** Each part of a two-part call writes through a slice and a slice of a slice of its own view. */
    static void partitionedSlices() throws Exception {
        for (int round = 0; round < 50; round++) {
            var data = zeros(8);
            var pl = new PartitionableList<>(data);
            PartitionableList.runPartitioned(TWO_PARTS, pl.readWrite(), (v) -> {
                int k = v.partitionNum() + 1;
                var s = new ReadWritePartListView<>(v, 2, v.end() - 2);
                check(s.partitionNum() == v.partitionNum(), "a slice keeps its parent's partition number");
                check(s.sourceBegin() == v.sourceBegin() + 2, "a slice starts at the parent's offset plus its own");
                s.set(0, 10.0 * k);
                var ss = new ReadWritePartListView<>(new ReadWritePartListView<>(v, 0, 3), 1, 1);
                ss.set(0, 100.0 * k);
                check(ss.get(0) == v.get(1), "a slice of a slice reads its parent's cell");
                expectOutOfRange(() -> new ReadWritePartListView<>(v, 2, 3), "a slice past the part's end");
                expectOutOfRange(() -> new ReadWritePartListView<>(v, -1, 2), "a slice before the part's start");
            }).get(10, TimeUnit.SECONDS);
            check(data.equals(List.of(0.0, 100.0, 10.0, 0.0, 0.0, 200.0, 20.0, 0.0)), "slices landed in their own parts: " + data);
        }
    }

    /** Views built as part 1 of (0, 4), (4, 8) would be, checked directly. */
    static void directViews() {
        var data = zeros(8);
        for (int i = 0; i < 8; i++) {
            data.set(i, (double) i);
        }

        // Read-write slice and slice of a slice.
        var rw = new ReadWritePartListView<>(data, 4, 4, 0, 1, null);
        var rs = new ReadWritePartListView<>(rw, 1, 3);
        check(rs.get(0) == 5.0 && rs.get(2) == 7.0, "read-write slice reads base 5..7");
        var rss = new ReadWritePartListView<>(rs, 1, 2);
        check(rss.get(0) == 6.0 && rss.get(1) == 7.0, "read-write slice of a slice reads base 6..7");
        rss.set(1, -7.0);
        check(data.get(7) == -7.0 && data.get(3) == 3.0, "slice of a slice writes base 7, not base 3");
        data.set(7, 7.0);
        expectOutOfRange(() -> new ReadWritePartListView<>(rs, 1, 3), "a slice of a slice past its parent slice");
        expectOutOfRange(() -> new ReadWritePartListView<>(rs, 0, -1), "a negative size");
        expectOutOfRange(() -> new ReadWritePartListView<>(rw, Integer.MAX_VALUE, 2), "an offset that overflows");
        check(new ReadWritePartListView<>(rw, 4, 0).writableSize() == 0, "an empty slice at the end is in range");

        // A read-write view with ghosts: its slice may read the ghosts through its own ghost cells, but may not
        // write them.
        var rwg = new ReadWritePartListView<>(data, 4, 2, 1, 1, null);
        var rwgs = new ReadWritePartListView<>(rwg, 0, 2, 1);
        check(rwgs.get(-1) == 3.0 && rwgs.get(2) == 6.0, "read-write slice with ghosts reads base 3 and 6");
        expectOutOfRange(() -> new ReadWritePartListView<>(rwg, -1, 2), "a writable slice over a ghost");
        expectOutOfRange(() -> new ReadWritePartListView<>(rwg, 0, 2, 2), "a slice whose ghosts pass the parent's");

        // Read-only view of a 3-cell part at base 4 with one ghost on each side: readable [-1, 4), base 3..7.
        var ro = new ReadOnlyPartListView<>(data, 4, 3, 1, 1, null);
        var ros = new ReadOnlyPartListView<>(ro, -1, 5);
        check(ros.get(0) == 3.0 && ros.get(4) == 7.0, "read-only slice over the ghosts reads base 3..7");
        var ross = new ReadOnlyPartListView<>(ros, 2, 2);
        check(ross.get(0) == 5.0 && ross.get(1) == 6.0, "read-only slice of a slice reads base 5..6");
        check(ross.partitionNum() == 1, "a read-only slice keeps its parent's partition number");
        expectOutOfRange(() -> new ReadOnlyPartListView<>(ro, -2, 3), "a read-only slice before the ghosts");
        expectOutOfRange(() -> new ReadOnlyPartListView<>(ro, 1, 4), "a read-only slice past the ghosts");
        expectOutOfRange(() -> new ReadOnlyPartListView<>(ros, 3, 3), "a read-only slice of a slice past it");

        // Write-only view: no readable range, so only the writable range counts.
        var wo = new WriteOnlyPartListView<>(data, 4, 4, 0, 1, null);
        var wos = new WriteOnlyPartListView<>(wo, 2, 2);
        wos.set(0, -6.0);
        check(data.get(6) == -6.0 && data.get(2) == 2.0, "write-only slice writes base 6, not base 2");
        expectOutOfRange(() -> new WriteOnlyPartListView<>(wo, 2, 3), "a write-only slice past the part's end");
        expectOutOfRange(() -> new WriteOnlyPartListView<>(wos, 1, 2), "a write-only slice of a slice past it");
    }

    /** A partitioned read with ghosts, slicing each part's view as the stencil reads would. */
    static void partitionedReadSlices() throws Exception {
        // Ten cells, one ghost: parts at base 1..4 and 5..8, each readable one cell further on both sides.
        var data = new ArrayList<Double>();
        for (int i = 0; i < 10; i++) {
            data.add((double) i);
        }
        var pl = new PartitionableList<>(data);
        PartitionableList.runPartitioned(2, 1, pl.read(), (v) -> {
            var left = new ReadOnlyPartListView<>(v, -1, v.end() + 1);
            var right = new ReadOnlyPartListView<>(v, 1, v.end() - 1);
            for (int i = 0; i < 4; i++) {
                check(left.get(i) == v.get(i - 1), "left slice of part " + v.partitionNum() + " at " + i);
                check(right.get(i) == v.get(i + 1), "right slice of part " + v.partitionNum() + " at " + i);
            }
            check(right.get(0) == v.sourceBegin() + 2, "right slice of part " + v.partitionNum() + " starts at base");
        }).get(10, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        directViews();
        partitionedSlices();
        partitionedReadSlices();
        System.out.println("TestPartViewSlice passed");
    }
}
