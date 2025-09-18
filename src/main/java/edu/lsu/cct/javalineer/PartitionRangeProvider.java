package edu.lsu.cct.javalineer;

public interface PartitionRangeProvider {
    int numPartitions();
    int getWritableBegin(int partitionNum);
    int getWritableEnd(int partitionNum);

    default int getReadableBegin(int partitionNum, int nGhosts) {
        return getWritableBegin(partitionNum) - nGhosts;
    }

    default int getReadableEnd(int partitionNum, int nGhosts) {
        return getWritableEnd(partitionNum) + nGhosts;
    }
}
