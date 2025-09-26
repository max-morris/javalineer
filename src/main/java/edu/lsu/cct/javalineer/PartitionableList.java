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
    private final GuardVar<Boolean> busy;
    private final CondContext<CondTask1<Boolean>> notBusy;

    public PartitionableList(List<E> data) {
        this.data = data;
        this.busy = new GuardVar<>(false);
        this.notBusy = Guard.newCondition(this.busy);
    }

    public PartitionableList() {
        this(new ArrayList<>());
    }

    public PartitionableList(int size, IntFunction<E> fillFunction) {
        this.data = new ArrayList<E>(size);
        for (int i = 0; i < size; i++) {
            this.data.add(fillFunction.apply(i));
        }
        this.busy = new GuardVar<>(false);
        this.notBusy = Guard.newCondition(this.busy);
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

    public static <T> CompletableFuture<Void> runPartitioned(int nChunks,
                                                             int nGhosts,
                                                             ReadOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadOnly(nChunks, nGhosts, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(int nChunks,
                                                             ReadOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadOnly(nChunks, 0, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(int nChunks,
                                                             int nGhosts,
                                                             WriteOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     WriteOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedWriteOnly(nChunks, nGhosts, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(int nChunks,
                                                             WriteOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     WriteOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedWriteOnly(nChunks, 0, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(int nChunks,
                                                             ReadWritePartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadWritePartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadWrite(nChunks, 0, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges,
                                                             int nGhosts,
                                                             ReadOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadOnly(ranges, nGhosts, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges,
                                                             ReadOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadOnly(ranges, 0, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges,
                                                             int nGhosts,
                                                             WriteOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     WriteOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedWriteOnly(ranges, nGhosts, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges,
                                                             WriteOnlyPartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     WriteOnlyPartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedWriteOnly(ranges, 0, chunkTask);
    }

    public static <T> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges,
                                                             ReadWritePartIntent<T> pi,
                                                             VoidPartTask1<
                                                                     ReadWritePartListView<T>
                                                                     > chunkTask) {
        return pi.getUnderlying().runPartitionedReadWrite(ranges, 0, chunkTask);
    }

    /* CODEGEN: generatePartitionableListOverloads */

    public CompletableFuture<Void> runPartitionedReadWrite(int nChunks,
                                                           int nGhosts,
                                                           VoidPartTask1<ReadWritePartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();

            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = partIndex(i, nChunks, dataSize, nGhosts);
                final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = partIndex(nChunks - 1, nChunks, dataSize, nGhosts);
            final var hi = partIndex(nChunks, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadOnly(int nChunks,
                                                          int nGhosts,
                                                          VoidPartTask1<ReadOnlyPartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();

            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = partIndex(i, nChunks, dataSize, nGhosts);
                final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = partIndex(nChunks - 1, nChunks, dataSize, nGhosts);
            final var hi = partIndex(nChunks, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedWriteOnly(int nChunks,
                                                           int nGhosts,
                                                           VoidPartTask1<WriteOnlyPartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = partIndex(i, nChunks, dataSize, nGhosts);
                final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = partIndex(nChunks - 1, nChunks, dataSize, nGhosts);
            final var hi = partIndex(nChunks, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadWrite(PartitionRangeProvider ranges,
                                                           int nGhosts,
                                                           VoidPartTask1<ReadWritePartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var nChunks = ranges.numPartitions();
            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = ranges.getWritableBegin(i);
                final var hi = ranges.getWritableEnd(i);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = ranges.getWritableBegin(nChunks - 1);
            final var hi = ranges.getWritableEnd(nChunks - 1);
            final var chunkSize = hi - lo;
            final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadOnly(PartitionRangeProvider ranges,
                                                          int nGhosts,
                                                          VoidPartTask1<ReadOnlyPartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var nChunks = ranges.numPartitions();
            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = ranges.getWritableBegin(i);
                final var hi = ranges.getWritableEnd(i);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = ranges.getWritableBegin(nChunks - 1);
            final var hi = ranges.getWritableEnd(nChunks - 1);
            final var chunkSize = hi - lo;
            final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedWriteOnly(PartitionRangeProvider ranges,
                                                           int nGhosts,
                                                           VoidPartTask1<WriteOnlyPartListView<E>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var nChunks = ranges.numPartitions();
            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = ranges.getWritableBegin(i);
                final var hi = ranges.getWritableEnd(i);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.run(view);
                    tasksDone.signal();
                });
            }
            final var lo = ranges.getWritableBegin(nChunks - 1);
            final var hi = ranges.getWritableEnd(nChunks - 1);
            final var chunkSize = hi - lo;
            final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.run(view);
            tasksDone.signal();

            tasksDone.getFut().thenRun(() -> {
                done.complete(null);
                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return done;
    }

    public CompletableFuture<E> reducePartitioned(int nChunks,
                                                  Function<ReadOnlyPartListView<E>, CompletableFuture<E>> chunkTask) {
        return reducePartitioned(nChunks, 0, chunkTask);
    }

    public CompletableFuture<E> reducePartitioned(int nChunks,
                                                  int nGhosts,
                                                  Function<ReadOnlyPartListView<E>, CompletableFuture<E>> chunkTask) {
        var result = new CompletableFuture<E>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();

            final var tasksDone = new CountdownLatch(nChunks);

            final var intermediateList = new ArrayList<E>(nChunks);
            final var intermediateGuard = new Guard();

            for (int i = 0; i < nChunks - 1; i++) {
                final var lo = partIndex(i, nChunks, dataSize, nGhosts);
                final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
                final var chunkSize = hi - lo;
                final var partNum = i;
                Pool.run(() -> {
                    final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum);
                    chunkTask.apply(view).thenAccept(res -> {
                        Guard.runGuarded(intermediateGuard, () -> {
                            intermediateList.add(res);
                            tasksDone.signal();
                        });
                    });
                });
            }

            final var lo = partIndex(nChunks - 1, nChunks, dataSize, nGhosts);
            final var hi = partIndex(nChunks, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var viewLast = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, nChunks - 1);
            chunkTask.apply(viewLast).thenAccept(res -> {
                Guard.runGuarded(intermediateGuard, () -> {
                    intermediateList.add(res);
                    tasksDone.signal();
                });
            });

            tasksDone.getFut().thenRun(() -> {
                assert nChunks == intermediateList.size();
                final var view = new ReadOnlyPartListView<>(intermediateList, 0, nChunks, 0, 0);

                chunkTask.apply(view).thenAccept(result::complete);

                Guard.runGuarded(busy, busyVar1 -> {
                    busyVar1.set(false);
                    notBusy.signal();
                });
            });

            return true;
        });

        return result;
    }
}
