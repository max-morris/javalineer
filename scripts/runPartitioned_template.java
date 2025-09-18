public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runPartitioned(int nChunks, int nGhosts,
                                                                          WriteOnlyPartIntent<T1> pi1,
                                                                          WriteOnlyPartIntent<T2> pi2,
                                                                          WriteOnlyPartIntent<T3> pi3,
                                                                          WriteOnlyPartIntent<T4> pi4,
                                                                          WriteOnlyPartIntent<T5> pi5,
                                                                          PartTask5<
                                                                                  WriteOnlyPartListView<T1>,
                                                                                  WriteOnlyPartListView<T2>,
                                                                                  WriteOnlyPartListView<T3>,
                                                                                  WriteOnlyPartListView<T4>,
                                                                                  WriteOnlyPartListView<T5>,
                                                                                  CompletableFuture<Void>
                                                                          > chunkTask) {
    final var done = new CompletableFuture<Void>();
    final var tasksDone = new CountdownLatch(nChunks);

    final var list1 = pi1.getUnderlying();
    final var list2 = pi2.getUnderlying();
    final var list3 = pi3.getUnderlying();
    final var list4 = pi4.getUnderlying();
    final var list5 = pi5.getUnderlying();

    final var listsNotBusy = Guard.newCondition(
            list1.busy,
            list2.busy,
            list3.busy,
            list4.busy,
            list5.busy
    );

    final var begun = new AtomicBoolean();

    Guard.runCondition(listsNotBusy, (busy1, busy2, busy3, busy4, busy5) -> {
        if (busy1.get() || busy2.get() || busy3.get() || busy4.get() || busy5.get()) {
            return false;
        }

        busy1.set(true);
        busy2.set(true);
        busy3.set(true);
        busy4.set(true);
        busy5.set(true);

        begun.set(true);

        for (int partNum = 0; partNum < nChunks - 1; partNum++) {
            final var data1 = list1.data;
            final var dataSize1 = data1.size();
            final var lo1 = partIndex(partNum, nChunks, dataSize1, nGhosts);
            final var hi1 = partIndex(partNum + 1, nChunks, dataSize1, nGhosts);
            final var chunkSize1 = hi1 - lo1;
            final var view1 = new WriteOnlyPartListView<>(data1, lo1, chunkSize1, nGhosts, partNum);

            final var data2 = list2.data;
            final var dataSize2 = data2.size();
            final var lo2 = partIndex(partNum, nChunks, dataSize2, nGhosts);
            final var hi2 = partIndex(partNum + 1, nChunks, dataSize2, nGhosts);
            final var chunkSize2 = hi2 - lo2;
            final var view2 = new WriteOnlyPartListView<>(data2, lo2, chunkSize2, nGhosts, partNum);

            final var data3 = list3.data;
            final var dataSize3 = data3.size();
            final var lo3 = partIndex(partNum, nChunks, dataSize3, nGhosts);
            final var hi3 = partIndex(partNum + 1, nChunks, dataSize3, nGhosts);
            final var chunkSize3 = hi3 - lo3;
            final var view3 = new WriteOnlyPartListView<>(data3, lo3, chunkSize3, nGhosts, partNum);

            final var data4 = list4.data;
            final var dataSize4 = data4.size();
            final var lo4 = partIndex(partNum, nChunks, dataSize4, nGhosts);
            final var hi4 = partIndex(partNum + 1, nChunks, dataSize4, nGhosts);
            final var chunkSize4 = hi4 - lo4;
            final var view4 = new WriteOnlyPartListView<>(data4, lo4, chunkSize4, nGhosts, partNum);

            final var data5 = list5.data;
            final var dataSize5 = data5.size();
            final var lo5 = partIndex(partNum, nChunks, dataSize5, nGhosts);
            final var hi5 = partIndex(partNum + 1, nChunks, dataSize5, nGhosts);
            final var chunkSize5 = hi5 - lo5;
            final var view5 = new WriteOnlyPartListView<>(data5, lo5, chunkSize5, nGhosts, partNum);

            Pool.run(() -> {
                chunkTask.apply(view1, view2, view3, view4, view5).thenRun(tasksDone::signal);
            });
        }

        final var partNum = nChunks - 1;

        final var data1 = list1.data;
        final var dataSize1 = data1.size();
        final var lo1 = partIndex(partNum, nChunks, dataSize1, nGhosts);
        final var hi1 = partIndex(partNum + 1, nChunks, dataSize1, nGhosts);
        final var chunkSize1 = hi1 - lo1;
        final var view1 = new WriteOnlyPartListView<>(data1, lo1, chunkSize1, nGhosts, partNum);

        final var data2 = list2.data;
        final var dataSize2 = data2.size();
        final var lo2 = partIndex(partNum, nChunks, dataSize2, nGhosts);
        final var hi2 = partIndex(partNum + 1, nChunks, dataSize2, nGhosts);
        final var chunkSize2 = hi2 - lo2;
        final var view2 = new WriteOnlyPartListView<>(data2, lo2, chunkSize2, nGhosts, partNum);

        final var data3 = list3.data;
        final var dataSize3 = data3.size();
        final var lo3 = partIndex(partNum, nChunks, dataSize3, nGhosts);
        final var hi3 = partIndex(partNum + 1, nChunks, dataSize3, nGhosts);
        final var chunkSize3 = hi3 - lo3;
        final var view3 = new WriteOnlyPartListView<>(data3, lo3, chunkSize3, nGhosts, partNum);

        final var data4 = list4.data;
        final var dataSize4 = data4.size();
        final var lo4 = partIndex(partNum, nChunks, dataSize4, nGhosts);
        final var hi4 = partIndex(partNum + 1, nChunks, dataSize4, nGhosts);
        final var chunkSize4 = hi4 - lo4;
        final var view4 = new WriteOnlyPartListView<>(data4, lo4, chunkSize4, nGhosts, partNum);

        final var data5 = list5.data;
        final var dataSize5 = data5.size();
        final var lo5 = partIndex(partNum, nChunks, dataSize5, nGhosts);
        final var hi5 = partIndex(partNum + 1, nChunks, dataSize5, nGhosts);
        final var chunkSize5 = hi5 - lo5;
        final var view5 = new WriteOnlyPartListView<>(data5, lo5, chunkSize5, nGhosts, partNum);

        chunkTask.apply(view1, view2, view3, view4, view5).thenRun(tasksDone::signal);

        tasksDone.getFut().thenRun(() -> {
            done.complete(null);

            Guard.runGuarded(list1.busy, busy1_ -> {
                busy1_.set(false);
                list1.notBusy.signalAll();
            });

            Guard.runGuarded(list2.busy, busy2_ -> {
                busy2_.set(false);
                list2.notBusy.signalAll();
            });

            Guard.runGuarded(list3.busy, busy3_ -> {
                busy3_.set(false);
                list3.notBusy.signalAll();
            });

            Guard.runGuarded(list4.busy, busy4_ -> {
                busy4_.set(false);
                list4.notBusy.signalAll();
            });

            Guard.runGuarded(list5.busy, busy5_ -> {
                busy5_.set(false);
                list5.notBusy.signalAll();
            });
        });

        return true;
    });

    if (!begun.get()) {
        Guard.runCondition(list1.notBusy, busy1 -> {
            listsNotBusy.signal();
            return begun.get();
        });

        Guard.runCondition(list2.notBusy, busy2 -> {
            listsNotBusy.signal();
            return begun.get();
        });

        Guard.runCondition(list3.notBusy, busy3 -> {
            listsNotBusy.signal();
            return begun.get();
        });

        Guard.runCondition(list4.notBusy, busy4 -> {
            listsNotBusy.signal();
            return begun.get();
        });

        Guard.runCondition(list5.notBusy, busy5 -> {
            listsNotBusy.signal();
            return begun.get();
        });
    }

    return done;
}