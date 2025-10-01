package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean; // Used in generated code
import java.util.function.Function;
import java.util.function.IntFunction;

@SuppressWarnings({"unused", "CodeBlock2Expr"})
public class PartitionableList<E> {
    private final List<E> data;
    private final GuardVar<RangeAccountant> rangeAccountant;
    private final CondContext<CondTask1<RangeAccountant>> rangeAccountantCond;

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
        final var tasksDone = new CountdownLatch(nChunks);

        final var intermediateList = new ArrayList<E>(nChunks);
        final var intermediateGuard = new Guard();

        final var dataSize = data.size();

        for (int i = 0; i < nChunks; i++) {
            final var lo = partIndex(i, nChunks, dataSize, nGhosts);
            final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.ReadOnly, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                chunkTask.apply(view).thenAccept(res -> {
                    this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                        rangeAccountantVar.get().release(PartIntentKind.ReadOnly, lo, hi, nGhosts);
                        this.rangeAccountantCond.signalAll();
                    });

                    Guard.runGuarded(intermediateGuard, () -> {
                        intermediateList.add(res);
                        tasksDone.signal();
                    });
                });
            };

            if (partNum == nChunks - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            assert nChunks == intermediateList.size();
            final var view = new ReadOnlyPartListView<>(intermediateList, 0, nChunks, 0, 0, this);
            chunkTask.apply(view).thenAccept(result::complete);
        });

        return result;
    }
}
