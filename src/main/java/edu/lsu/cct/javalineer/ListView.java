package edu.lsu.cct.javalineer;

import java.util.Iterator;
import java.util.List;

public class ListView<E> implements Iterable<E> {
    private final List<E> underlying;
    private final int offset, size;

    public ListView(List<E> underlying, int offset, int size) {
        this.underlying = underlying;
        this.offset = offset;
        this.size = size;
    }

    public ListView(ListView<E> other, int offset, int size) {
        this.underlying = other.underlying;
        this.offset = other.offset + offset;
        this.size = size;
    }

    public E getUnchecked(int i) {
        return underlying.get(offset + i);
    }

    public void setUnchecked(int i, E e) {
        underlying.set(offset + i, e);
    }

    public E get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        return getUnchecked(i);
    }

    public void set(int i, E e) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        setUnchecked(i, e);
    }

    public int size() {
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<>() {
            private int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < size;
            }

            @Override
            public E next() {
                return getUnchecked(idx++);
            }
        };
    }
}
