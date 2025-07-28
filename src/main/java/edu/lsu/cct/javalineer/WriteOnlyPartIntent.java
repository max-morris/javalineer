package edu.lsu.cct.javalineer;

public class WriteOnlyPartIntent<T> extends PartIntent<T> {
    public WriteOnlyPartIntent(PartitionableList<T> underlying) {
        super(PartIntentKind.WriteOnly, underlying);
    }
}
