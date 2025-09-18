public static <T1, T2, T3, T4, T5> CompletableFuture<Void> runPartitioned(PartitionRangeProvider ranges, int nGhosts,
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
    final var tasksDone = new CountdownLatch(ranges.numPartitions());

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

        for (int partNum = 0; partNum < ranges.numPartitions() - 1; partNum++) {
            final var data1 = list1.data;
            final var lo1 = ranges.getWritableBegin(partNum) ;
            final var hi1 = ranges.getWritableEnd(partNum);
            final var chunkSize1 = hi1 - lo1;
            final var view1 = new WriteOnlyPartListView<>(data1, lo1, chunkSize1, nGhosts, partNum);

            ...

            Pool.run(() -> {
                chunkTask.apply(view1, view2, view3, view4, view5).thenRun(tasksDone::signal);
            });
        }

        final var partNum = ranges.numPartitions() - 1;

        final var data1 = list1.data;
        final var lo1 = ranges.getWritableBegin(partNum);
        final var hi1 = ranges.getWritableEnd(partNum);
        final var chunkSize1 = hi1 - lo1;
        final var view1 = new WriteOnlyPartListView<>(data1, lo1, chunkSize1, nGhosts, partNum);

        ...

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