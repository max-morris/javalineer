package edu.lsu.cct.javalineer;

public class ReadOnlyPartIntent<T> extends PartIntent<T> {
    public ReadOnlyPartIntent(PartitionableList<T> underlying) {
        super(PartIntentKind.ReadOnly, underlying);
    }
}
