package edu.lsu.cct.javalineer;

import java.util.List;

public class ListView<E> {
    private final List<E> underlying;
    private final int offset, size;

    public ListView(List<E> underlying, int offset, int size) {
        this.underlying = underlying;
        this.offset = offset;
        this.size = size;
    }

    public E get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        return underlying.get(offset + i);
    }

    public void set(int i, E e) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        underlying.set(offset + i, e);
    }

    public int size() {
        return size;
    }
}
