package edu.lsu.cct.javalineer;

import java.util.*;
import java.util.Set;

/**
 * The ranges of a PartitionableList held by partitioned calls: read ranges counted, write ranges each held once. An
 * empty (or inverted) write range writes nothing, conflicts with nothing and is not recorded, so that two calls on an
 * empty list (or parts with no writable cells) can hold their write ranges at once.
 */
public class RangeAccountant {
    private final Map<Range, Integer> readRanges = new HashMap<>();
    private final Set<Range> writeRanges = new HashSet<>();

    @SuppressWarnings("AssignmentUsedAsCondition")
    public boolean isRangeOk(PartIntentKind kind, int startDesired, int endDesired, int nGhosts) {
        boolean ok;
        switch (kind) {
            case ReadOnly:
                if (ok = isReadOk(startDesired - nGhosts, endDesired + nGhosts)) {
                    insertRead(new Range(startDesired - nGhosts, endDesired + nGhosts));
                }
                return ok;
            case WriteOnly:
                if (ok = isWriteOk(startDesired, endDesired)) {
                    insertWrite(startDesired, endDesired);
                }
                return ok;
            case ReadWrite:
                if (ok = isReadOk(startDesired - nGhosts, endDesired + nGhosts) && isWriteOk(startDesired, endDesired)) {
                    insertRead(new Range(startDesired - nGhosts, endDesired + nGhosts));
                    insertWrite(startDesired, endDesired);
                }
                return ok;
            default:
                throw new IllegalArgumentException("Invalid intent kind: " + kind);
        }
    }

    public void release(PartIntentKind kind, int startDesired, int endDesired, int nGhosts) {
        Range rr;
        switch (kind) {
            case ReadOnly:
                rr = new Range(startDesired - nGhosts, endDesired + nGhosts);
                assert readRanges.containsKey(rr);
                removeRead(rr);
                break;
            case WriteOnly:
                removeWrite(startDesired, endDesired);
                break;
            case ReadWrite:
                rr = new Range(startDesired - nGhosts, endDesired + nGhosts);
                assert readRanges.containsKey(rr);
                removeRead(rr);
                removeWrite(startDesired, endDesired);
                break;
        }
    }

    private void insertWrite(int start, int end) {
        if (start < end) {
            var inserted = writeRanges.add(new Range(start, end));
            assert inserted;
        }
    }

    private void removeWrite(int start, int end) {
        if (start < end) {
            var removed = writeRanges.remove(new Range(start, end));
            assert removed;
        }
    }

    private void removeRead(Range rr) {
        readRanges.computeIfPresent(rr, (k, v) -> {
            if (v.equals(1)) {
                return null;
            } else {
                return v - 1;
            }
        });
    }

    private void insertRead(Range rr) {
        readRanges.compute(rr, (k, v) -> {
            if (v == null) {
                return 1;
            } else {
                return v + 1;
            }
        });
    }

    private boolean isReadOk(int startDesired, int endDesired) {
        for (var range : writeRanges) {
            if (range.start < endDesired && range.end > startDesired) {
                return false;
            }
        }

        return true;
    }

    private boolean isWriteOk(int startDesired, int endDesired) {
        if (startDesired >= endDesired) {
            return true;
        }
        if (!isReadOk(startDesired, endDesired)) {
            return false;
        }

        for (var range : readRanges.keySet()) {
            if (range.start < endDesired && range.end > startDesired) {
                return false;
            }
        }

        return true;
    }

    private static class Range {
        int start;
        int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Range range = (Range) o;
            return start == range.start && end == range.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }
}
