package edu.lsu.cct.javalineer;

public abstract class PartIntent<T> {
    private final PartIntentKind intentKind;
    private final PartitionableList<T> underlying;

    PartIntent(PartIntentKind intentKind, PartitionableList<T> underlying) {
        this.intentKind = intentKind;
        this.underlying = underlying;
    }

    public PartIntentKind getIntentKind() {
        return intentKind;
    }

    public PartitionableList<T> getUnderlying() {
        return underlying;
    }
}
