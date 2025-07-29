package edu.lsu.cct.javalineer;

import java.util.Iterator;

interface ReadView<T> extends View<T>, Iterable<T> {
    T get(int i);
    T getUnchecked(int i);

    static <T> Iterator<T> getViewIterator(final ReadView<T> readView) {
        return new Iterator<>() {
            private int idx = 0;
            private final int size = readView.readableSize();
            private final int ghostSize = readView.ghostSize();

            @Override
            public boolean hasNext() {
                return idx < size;
            }

            @Override
            public T next() {
                return readView.getUnchecked(idx++ - ghostSize);
            }
        };
    }
}
