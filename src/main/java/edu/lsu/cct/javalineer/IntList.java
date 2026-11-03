package edu.lsu.cct.javalineer;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.IntFunction;

/**
 * A fixed-size List<Integer> whose elements live in a int[]: the storage of a PartitionableList of ints
 * ({@link PartitionableList#ofInt}). The boxed get and set read and write the same storage, so code which treats it as
 * a List<Integer> works; the views' getInt and setInt never box.
 */
public final class IntList extends AbstractList<Integer> implements RandomAccess {
    final int[] data;

    public IntList(int size) {
        data = new int[size];
    }

    public IntList(int size, IntFunction<Integer> fillFunction) {
        this(size);
        for (int i = 0; i < data.length; i++) {
            data[i] = fillFunction.apply(i);
        }
    }

    public IntList(List<Integer> contents) {
        this(contents.size());
        for (int i = 0; i < data.length; i++) {
            data[i] = contents.get(i);
        }
    }

    public int getInt(int index) {
        return data[index];
    }

    public void setInt(int index, int value) {
        data[index] = value;
    }

    @Override
    public Integer get(int index) {
        return data[index];
    }

    @Override
    public Integer set(int index, Integer element) {
        int old = data[index];
        data[index] = element;
        return old;
    }

    @Override
    public int size() {
        return data.length;
    }
}
