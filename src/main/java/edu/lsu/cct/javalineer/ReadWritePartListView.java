package edu.lsu.cct.javalineer;

import java.util.Iterator;
import java.util.List;

public class ReadWritePartListView<E> extends PartListView<E> implements ReadWriteView<E> {
    public ReadWritePartListView(List<E> underlying, int offset, int size, int ghostSize) {
        super(underlying, offset, size, ghostSize);
    }

    public ReadWritePartListView(List<E> underlying, int offset, int size) {
        super(underlying, offset, size);
    }

    public ReadWritePartListView(ReadWritePartListView<E> other, int offset, int size, int ghostSize) {
        super(other, offset, size, ghostSize);
    }

    public ReadWritePartListView(ReadWritePartListView<E> other, int offset, int size) {
        super(other, offset, size);
    }

    /*public static <E> ReadWritePartListView<E> newChecked(List<E> underlying, int offset, int size) {
        if (offset < 0 || offset + size > underlying.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, underlying.size()));
        }
        return new ReadWritePartListView<>(underlying, offset, size);
    }

    public static <E> ReadWritePartListView<E> newChecked(ReadWritePartListView<E> other, int offset, int size) {
        if (offset < 0 || offset + size > other.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, other.size()));
        }
        return new ReadWritePartListView<>(other, offset, size);
    }*/

    @Override
    public E getUnchecked(int i) {
        return underlying.get(offset + i);
    }

    @Override
    public void setUnchecked(int i, E e) {
        underlying.set(offset + i, e);
    }

    @Override
    public E get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        return getUnchecked(i);
    }

    @Override
    public void set(int i, E e) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        setUnchecked(i, e);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        return ReadView.getViewIterator(this);
    }
}
