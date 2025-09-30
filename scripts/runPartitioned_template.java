public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runPartitioned(int nChunks int nGhosts,
                                                                          WriteOnlyPartIntent<T1> pi1,
                                                                          WriteOnlyPartIntent<T2> pi2,
                                                                          WriteOnlyPartIntent<T3> pi3,
                                                                          WriteOnlyPartIntent<T4> pi4,
                                                                          WriteOnlyPartIntent<T5> pi5,
                                                                          VoidPartTask5<
                                                                                  WriteOnlyPartListView<T1>,
                                                                                  WriteOnlyPartListView<T2>,
                                                                                  WriteOnlyPartListView<T3>,
                                                                                  WriteOnlyPartListView<T4>,
                                                                                  WriteOnlyPartListView<T5>
                                                                                  > chunkTask) {
    final var done = new CompletableFuture<Void>();
    final var tasksDone = new CountdownLatch(nChunks);

    for (int i = 0; i < nChunks; i++) {
        final var data1 = list1.data;
        final var dataSize1 = data1.size();
        final var lo1 = partIndex(partNum, nChunks, dataSize1, nGhosts);
        final var hi1 = partIndex(partNum + 1, nChunks, dataSize1, nGhosts);
        final var chunkSize1 = hi1 - lo1;
        final var view1 = new WriteOnlyPartListView<>(data1, lo1, chunkSize1, nGhosts, partNum);

        ...

        var ready1 = Guard.runCondition(pi1.getUnderlying().rangeAccountantCond, (rangeAccountantVar) -> {
            return rangeAccountantVar.get().isRangeOk(PartIntentKind.WriteOnly, lo, hi, nGhosts);
        });

        ...

        var ready = CompletableFuture.allOf(ready1, ready2, ready3, ready4, ready5);

        Runnable task = () -> {
            chunkTask.run(view);
            tasksDone.signal();

            pl1.rangeAccountant.runGuarded((rangeAccountantVar) -> {
                rangeAccountantVar.get().release(PartIntentKind.WriteOnly, lo, hi, nGhosts);
                pl1.rangeAccountantCond.signalAll();
            });
            ...
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