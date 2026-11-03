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

    /**
     * A slice of {@code other}. The offset is in {@code other}'s local indices (a part view's indices start at 0 in
     * every part), so the slice starts at {@code other.offset + offset} in the underlying list. The slice keeps
     * {@code other}'s partition number. Its range must lie within {@code other}'s: its readable range
     * {@code [offset - ghostSize, offset + size + ghostSize)} within {@code other}'s readable range when {@code other}
     * is readable, and its writable range {@code [offset, offset + size)} within {@code other}'s writable range when
     * {@code other} is writable. Otherwise this throws the {@link IndexOutOfBoundsException} that the bounds-checked
     * accessors throw, so a slice can never reach cells its parent cannot.
     */
    public PartListView(PartListView<E> other, int offset, int size, int ghostSize) {
        this(other.underlying, checkSlice(other, offset, size, ghostSize), size, ghostSize, other.partitionNum,
                other.source);
    }

    public PartListView(PartListView<E> other, int offset, int size) {
        this(other, offset, size, 0);
    }

    /** Checks a slice of {@code other} (see the slice constructor) and returns its offset in the underlying list. */
    private static int checkSlice(PartListView<?> other, int offset, int size, int ghostSize) {
        if (size < 0 || ghostSize < 0) {
            throw sliceOutOfRange(other, offset, size, ghostSize);
        }
        // Long arithmetic, so a huge offset or size cannot wrap around into range.
        long lo = offset, hi = (long) offset + size;
        if (other instanceof ReadView && (lo - ghostSize < other.begin() || hi + ghostSize > other.end())) {
            throw sliceOutOfRange(other, offset, size, ghostSize);
        }
        if (other instanceof WriteView && (lo < 0 || hi > other.writableSize())) {
            throw sliceOutOfRange(other, offset, size, ghostSize);
        }
        return other.offset + offset;
    }

    private static IndexOutOfBoundsException sliceOutOfRange(PartListView<?> other, int offset, int size, int ghostSize) {
        String readable = other instanceof ReadView ? String.format("[%d, %d)", other.begin(), other.end()) : "empty";
        String writable = other instanceof WriteView ? String.format("[0, %d)", other.writableSize()) : "empty";
        return new IndexOutOfBoundsException(String.format(
                "slice offset %d, size %d, ghosts %d out of range of a view with readable range %s and writable range %s",
                offset, size, ghostSize, readable, writable));
    }

    public PartitionableList<E> getSource() {
        return source;
    }
}
