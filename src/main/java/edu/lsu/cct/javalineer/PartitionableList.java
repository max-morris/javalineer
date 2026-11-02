package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean; // Used in generated code
import java.util.function.Function;
import java.util.function.IntFunction;

@SuppressWarnings({"unused", "CodeBlock2Expr"})
public final class PartitionableList<E> {
    final List<E> data;
    final GuardVar<RangeAccountant> rangeAccountant;
    final CondContext<CondTask1<RangeAccountant>> rangeAccountantCond;

    public PartitionableList(List<E> data) {
        this.data = data;
        this.rangeAccountant = new GuardVar<>(new RangeAccountant());
        this.rangeAccountantCond = CondContext.newCond(this.rangeAccountant);
    }

    public PartitionableList() {
        this(new ArrayList<>());
    }

    public PartitionableList(int size, IntFunction<E> fillFunction) {
        this.data = new ArrayList<E>(size);
        for (int i = 0; i < size; i++) {
            this.data.add(fillFunction.apply(i));
        }
        this.rangeAccountant = new GuardVar<>(new RangeAccountant());
        this.rangeAccountantCond = CondContext.newCond(this.rangeAccountant);
    }

    public int size() {
        return data.size();
    }

    public static <E> PartitionableList<E> of(int size, IntFunction<E> fillFunction) {
        return new PartitionableList<E>(size, fillFunction);
    }

    public static int partIndex(int part, int numParts, int size, int nGhosts) {
        int writableSize = size - 2 * nGhosts;
        int n = writableSize / numParts;
        int r = writableSize % numParts;
        return n * part + Math.min(part, r) + nGhosts;
    }

    public ReadOnlyPartIntent<E> read() {
        return new ReadOnlyPartIntent<>(this);
    }

    public WriteOnlyPartIntent<E> write() {
        return new WriteOnlyPartIntent<>(this);
    }

    public ReadWritePartIntent<E> readWrite() {
        return new ReadWritePartIntent<>(this);
    }

    /* CODEGEN: generatePartitionableListOverloads */

    public CompletableFuture<E> reducePartitioned(int nChunks,
                                                  Function<ReadOnlyPartListView<E>, CompletableFuture<E>> chunkTask) {
        return reducePartitioned(nChunks, 0, chunkTask);
    }

    public CompletableFuture<E> reducePartitioned(int nChunks,
                                                  int nGhosts,
                                                  Function<ReadOnlyPartListView<E>, CompletableFuture<E>> chunkTask) {
        final var result = new CompletableFuture<E>();
        // The call takes one read range for all of its parts and releases it when the last part's result is in
        // (PartCall.java): the final reduction starts only when every range of the call is free again.
        final var call = PartCall.ofChunks(nChunks, nGhosts, new PartitionableList<?>[] {this},
                                           new PartIntentKind[] {PartIntentKind.ReadOnly});
        // The parts' results, in part order. Each is written before its part's partDone, and read after the call's
        // future completes, which happens after every partDone.
        @SuppressWarnings("unchecked")
        final var intermediate = (E[]) new Object[nChunks];

        final var dataSize = data.size();

        call.start((partNum) -> {
            final var lo = partIndex(partNum, nChunks, dataSize, nGhosts);
            final var hi = partIndex(partNum + 1, nChunks, dataSize, nGhosts);
            final var view = new ReadOnlyPartListView<>(data, lo, hi - lo, nGhosts, partNum, this);
            Pool.callCapturing(() -> chunkTask.apply(view)).whenComplete((res, e) -> {
                if (e == null) {
                    intermediate[partNum] = res;
                }
                call.partDone(e);
            });
        });

        call.future().whenComplete((v, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
                return;
            }
            final var view = new ReadOnlyPartListView<>(Arrays.asList(intermediate), 0, nChunks, 0, 0, this);
            Pool.callCapturing(() -> chunkTask.apply(view)).whenComplete((res, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                } else {
                    result.complete(res);
                }
            });
        });

        return result;
    }
}
