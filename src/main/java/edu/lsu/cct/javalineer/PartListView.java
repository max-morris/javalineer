package edu.lsu.cct.javalineer;

import java.util.List;

public abstract class PartListView<E> implements View<E> {
    protected final List<E> underlying;
    protected final int offset, size, ghostSize, partitionNum;
    protected final PartitionableList<E> source;

    public PartListView(List<E> underlying, int offset, int size, int ghostSize, int partitionNum, PartitionableList<E> source) {
        this.underlying = underlying;
        this.offset = offset;
        this.size = size;
        this.ghostSize = ghostSize;
        this.partitionNum = partitionNum;
        this.source = source;
    }

    public PartListView(List<E> underlying, int offset, int size, PartitionableList<E> source) {
        this(underlying, offset, size, 0, 0, source);
    }

    public PartListView(PartListView<E> other, int offset, int size, int ghostSize) {
        this(other.underlying, offset, size, ghostSize, 0, other.source);
    }

    public PartListView(PartListView<E> other, int offset, int size) {
        this(other, offset, size, 0);
    }

    public PartitionableList<E> getSource() {
        return source;
    }
}
