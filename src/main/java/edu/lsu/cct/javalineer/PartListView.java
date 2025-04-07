package edu.lsu.cct.javalineer;

import java.util.List;

public abstract class PartListView<E> implements View<E> {
    protected final List<E> underlying;
    protected final int offset, size, ghostSize;

    public PartListView(List<E> underlying, int offset, int size, int ghostSize) {
        this.underlying = underlying;
        this.offset = offset;
        this.size = size;
        this.ghostSize = ghostSize;
    }

    public PartListView(List<E> underlying, int offset, int size) {
        this(underlying, offset, size, 0);
    }

    public PartListView(PartListView<E> other, int offset, int size, int ghostSize) {
        this(other.underlying, offset, size, ghostSize);
    }

    public PartListView(PartListView<E> other, int offset, int size) {
        this(other, offset, size, 0);
    }
}
