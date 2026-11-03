package edu.lsu.cct.javalineer;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.IntFunction;

/**
 * A fixed-size List<Double> whose elements live in a double[]: the storage of a PartitionableList of doubles
 * ({@link PartitionableList#ofDouble}). The boxed get and set read and write the same storage, so code which treats it as
 * a List<Double> works; the views' getDouble and setDouble never box.
 */
public final class DoubleList extends AbstractList<Double> implements RandomAccess {
    final double[] data;

    public DoubleList(int size) {
        data = new double[size];
    }

    public DoubleList(int size, IntFunction<Double> fillFunction) {
        this(size);
        for (int i = 0; i < data.length; i++) {
            data[i] = fillFunction.apply(i);
        }
    }

    public DoubleList(List<Double> contents) {
        this(contents.size());
        for (int i = 0; i < data.length; i++) {
            data[i] = contents.get(i);
        }
    }

    public double getDouble(int index) {
        return data[index];
    }

    public void setDouble(int index, double value) {
        data[index] = value;
    }

    @Override
    public Double get(int index) {
        return data[index];
    }

    @Override
    public Double set(int index, Double element) {
        double old = data[index];
        data[index] = element;
        return old;
    }

    @Override
    public int size() {
        return data.length;
    }
}
