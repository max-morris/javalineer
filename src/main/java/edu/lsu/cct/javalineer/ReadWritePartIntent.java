package edu.lsu.cct.javalineer;

public class ReadWritePartIntent<T> extends PartIntent<T> {
    public ReadWritePartIntent(PartitionableList<T> underlying) {
        super(PartIntentKind.ReadWrite, underlying);
    }
}
