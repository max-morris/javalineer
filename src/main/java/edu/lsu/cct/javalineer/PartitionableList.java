package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.CondTask1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

    public CompletableFuture<Void> runPartitioned(int nChunks,
                                                  Function<ListView<E>, CompletableFuture<Void>> chunkTask) {
        var done = new CompletableFuture<Void>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var chunkSize = dataSize / nChunks;
            final var remainderSize = dataSize % nChunks;

            final var tasksDone = new CountdownLatch(nChunks);

            for (int i = 0; i < nChunks - 1; i++) {
                final var chunkN = i;
                Pool.run(() -> {
                    final var view = new ListView<>(data, chunkN * chunkSize, chunkSize);
                    chunkTask.apply(view).thenRun(tasksDone::signal);
                });
            }

            Pool.run(() -> {
                final var view = new ListView<>(data, (nChunks - 1) * chunkSize, remainderSize == 0 ? chunkSize : remainderSize);
                chunkTask.apply(view).thenRun(tasksDone::signal);
            });

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
                                                  Function<ListView<E>, CompletableFuture<E>> chunkTask) {
        var result = new CompletableFuture<E>();

        Guard.runCondition(notBusy, busyVar -> {
            if (busyVar.get()) {
                return false;
            }

            busyVar.set(true);

            final var dataSize = data.size();
            final var chunkSize = dataSize / nChunks;
            final var remainderSize = dataSize % nChunks;

            final var tasksDone = new CountdownLatch(nChunks);

            final var intermediateList = new ArrayList<E>(nChunks);
            final var intermediateGuard = new Guard();

            for (int i = 0; i < nChunks - 1; i++) {
                final var chunkN = i;
                Pool.run(() -> {
                    final var view = new ListView<>(data, chunkN * chunkSize, chunkSize);
                    chunkTask.apply(view).thenAccept(res -> {
                        Guard.runGuarded(intermediateGuard, () -> {
                            intermediateList.add(res);
                            tasksDone.signal();
                        });
                    });
                });
            }

            Pool.run(() -> {
                final var view = new ListView<>(data, (nChunks - 1) * chunkSize, remainderSize == 0 ? chunkSize : remainderSize);
                chunkTask.apply(view).thenAccept(res -> {
                    Guard.runGuarded(intermediateGuard, () -> {
                        intermediateList.add(res);
                        tasksDone.signal();
                    });
                });
            });

            tasksDone.getFut().thenRun(() -> {
                assert nChunks == intermediateList.size();
                final var view = new ListView<>(intermediateList, 0, nChunks);

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
