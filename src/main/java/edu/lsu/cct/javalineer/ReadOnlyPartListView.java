package edu.lsu.cct.javalineer;

import java.util.Iterator;
import java.util.List;

public class ReadOnlyPartListView<E> extends PartListView<E> implements ReadView<E> {
    public ReadOnlyPartListView(List<E> underlying, int offset, int size, int ghostSize) {
        super(underlying, offset, size, ghostSize);
    }

    public ReadOnlyPartListView(List<E> underlying, int offset, int size) {
        super(underlying, offset, size);
    }

    public ReadOnlyPartListView(ReadOnlyPartListView<E> other, int offset, int size, int ghostSize) {
        super(other, offset, size, ghostSize);
    }

    public ReadOnlyPartListView(ReadOnlyPartListView<E> other, int offset, int size) {
        super(other, offset, size);
    }

    /*public static <E> ReadOnlyPartListView<E> newChecked(List<E> underlying, int offset, int size) {
        if (offset < 0 || offset + size > underlying.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, underlying.size()));
        }
        return new ReadOnlyPartListView<>(underlying, offset, size);
    }

    public static <E> ReadOnlyPartListView<E> newChecked(ReadOnlyPartListView<E> other, int offset, int size) {
        if (offset < 0 || offset + size > other.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, other.size()));
        }
        return new ReadOnlyPartListView<>(other, offset, size);
    }*/

    @Override
    public E getUnchecked(int i) {
        return underlying.get(offset + i);
    }

    @Override
    public E get(int i) {
        if (i + offset < 0 || i + offset >= underlying.size()) {
            throw new IndexOutOfBoundsException(i);
        }
        return getUnchecked(i);
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
