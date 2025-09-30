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
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(nChunks);

        final var dataSize = data.size();

        for (int i = 0; i < nChunks; i++) {
            final var lo = partIndex(i, nChunks, dataSize, nGhosts);
            final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.ReadWrite, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.ReadWrite, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == nChunks - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadOnly(int nChunks,
                                                          int nGhosts,
                                                          VoidPartTask1<ReadOnlyPartListView<E>> chunkTask) {
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(nChunks);

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
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.ReadOnly, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == nChunks - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedWriteOnly(int nChunks,
                                                           int nGhosts,
                                                           VoidPartTask1<WriteOnlyPartListView<E>> chunkTask) {
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(nChunks);

        final var dataSize = data.size();

        for (int i = 0; i < nChunks; i++) {
            final var lo = partIndex(i, nChunks, dataSize, nGhosts);
            final var hi = partIndex(i + 1, nChunks, dataSize, nGhosts);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.WriteOnly, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.WriteOnly, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == nChunks - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadWrite(PartitionRangeProvider ranges,
                                                           int nGhosts,
                                                           VoidPartTask1<ReadWritePartListView<E>> chunkTask) {
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(ranges.numPartitions());

        final var dataSize = data.size();

        for (int i = 0; i < ranges.numPartitions(); i++) {
            final var lo = ranges.getWritableBegin(i);
            final var hi = ranges.getWritableEnd(i);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.ReadWrite, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new ReadWritePartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.ReadWrite, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == ranges.numPartitions() - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedReadOnly(PartitionRangeProvider ranges,
                                                          int nGhosts,
                                                          VoidPartTask1<ReadOnlyPartListView<E>> chunkTask) {
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(ranges.numPartitions());

        final var dataSize = data.size();

        for (int i = 0; i < ranges.numPartitions(); i++) {
            final var lo = ranges.getWritableBegin(i);
            final var hi = ranges.getWritableEnd(i);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.ReadOnly, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new ReadOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.ReadOnly, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == ranges.numPartitions() - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
        });

        return done;
    }

    public CompletableFuture<Void> runPartitionedWriteOnly(PartitionRangeProvider ranges,
                                                           int nGhosts,
                                                           VoidPartTask1<WriteOnlyPartListView<E>> chunkTask) {
        final var done = new CompletableFuture<Void>();
        final var tasksDone = new CountdownLatch(ranges.numPartitions());

        final var dataSize = data.size();

        for (int i = 0; i < ranges.numPartitions(); i++) {
            final var lo = ranges.getWritableBegin(i);
            final var hi = ranges.getWritableEnd(i);
            final var chunkSize = hi - lo;
            final var partNum = i;

            var ready = Guard.runCondition(this.rangeAccountantCond, (rangeAccountantVar) -> {
                return rangeAccountantVar.get().isRangeOk(PartIntentKind.WriteOnly, lo, hi, nGhosts);
            });

            Runnable task = () -> {
                final var view = new WriteOnlyPartListView<>(data, lo, chunkSize, nGhosts, partNum, this);
                try {
                    chunkTask.run(view);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return;
                }
                tasksDone.signal();
                this.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                    rangeAccountantVar.get().release(PartIntentKind.WriteOnly, lo, hi, nGhosts);
                    this.rangeAccountantCond.signalAll();
                });
            };

            if (partNum == ranges.numPartitions() - 1) {
                ready.thenRun(task);
            } else {
                ready.thenRunAsync(task, Pool.getPool());
            }
        }

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);
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
