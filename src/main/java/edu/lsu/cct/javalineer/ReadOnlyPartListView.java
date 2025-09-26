package edu.lsu.cct.javalineer;

import java.util.Iterator;
import java.util.List;

public class ReadOnlyPartListView<E> extends PartListView<E> implements ReadView<E> {
    public ReadOnlyPartListView(List<E> underlying, int offset, int size, int ghostSize, int partitionNum, PartitionableList<E> source) {
        super(underlying, offset, size, ghostSize, partitionNum, source);
    }

    public ReadOnlyPartListView(List<E> underlying, int offset, int size, PartitionableList<E> source) {
        super(underlying, offset, size, source);
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
        if (i < -ghostSize || i >= size + ghostSize) {
            throw new IndexOutOfBoundsException(i);
        }
        return getUnchecked(i);
    }

    @Override
    public int readableSize() {
        return size + 2*ghostSize;
    }

    @Override
    public int writableSize() {
        return 0;
    }

    @Override
    public int ghostSize() {
        return ghostSize;
    }

    @Override
    public int partitionNum() {
        return partitionNum;
    }

    @Override
    public int begin() {
        return -ghostSize;
    }

    @Override
    public int end() {
        return size + ghostSize;
    }

    @Override
    public int size() {
        return readableSize();
    }

    @Override
    public Iterator<E> iterator() {
        return ReadView.getViewIterator(this);
    }

    @Override
    public int sourceBegin() {
        return offset - ghostSize;
    }

    @Override
    public int sourceEnd() {
        return offset + size + ghostSize;
    }
}
