package edu.lsu.cct.javalineer;

import edu.lsu.cct.javalineer.functionalinterfaces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class PartitionableListExt {
    public static int partIndex(int part, int numParts, int size, int nGhosts) {
        return PartitionableList.partIndex(part, numParts, size, nGhosts);
    }

    /* CODEGEN: generatePartitionableListOverloadsExt */
}
