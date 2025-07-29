package edu.lsu.cct.javalineer;

import java.util.List;

public class WriteOnlyPartListView<E> extends PartListView<E> implements WriteView<E> {
    public WriteOnlyPartListView(List<E> underlying, int offset, int size, int ghostSize, int partitionNum) {
        super(underlying, offset, size, ghostSize, partitionNum);
    }

    public WriteOnlyPartListView(List<E> underlying, int offset, int size) {
        super(underlying, offset, size);
    }

    public WriteOnlyPartListView(WriteOnlyPartListView<E> other, int offset, int size, int ghostSize) {
        super(other, offset, size, ghostSize);
    }

    public WriteOnlyPartListView(WriteOnlyPartListView<E> other, int offset, int size) {
        super(other, offset, size);
    }

    /*public static <E> WriteOnlyPartListView<E> newChecked(List<E> underlying, int offset, int size) {
        if (offset < 0 || offset + size > underlying.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, underlying.size()));
        }
        return new WriteOnlyPartListView<>(underlying, offset, size);
    }

    public static <E> WriteOnlyPartListView<E> newChecked(WriteOnlyPartListView<E> other, int offset, int size) {
        if (offset < 0 || offset + size > other.size()) {
            throw new IndexOutOfBoundsException(String.format("offset: %d, size: %d, underlying size: %d", offset, size, other.size()));
        }
        return new WriteOnlyPartListView<>(other, offset, size);
    }*/

    @Override
    public void setUnchecked(int i, E e) {
        underlying.set(offset + i, e);
    }

    @Override
    public void set(int i, E e) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException(i);
        }
        setUnchecked(i, e);
    }

    @Override
    public int readableSize() {
        return 0;
    }

    @Override
    public int writableSize() {
        return size;
    }

    @Override
    public int ghostSize() {
        return ghostSize;
    }

    @Override
    public int partitionNum() {
        return partitionNum;
    }
}
