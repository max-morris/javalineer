package edu.lsu.cct.javalineer;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class RangeAccountant {
    private final Set<Range> readRanges = new HashSet<>();
    private final Set<Range> writeRanges = new HashSet<>();

    @SuppressWarnings("AssignmentUsedAsCondition")
    public boolean isRangeOk(PartIntentKind kind, int startDesired, int endDesired, int nGhosts) {
        boolean ok;
        switch (kind) {
            case ReadOnly:
                if (ok = isReadOk(startDesired - nGhosts, endDesired + nGhosts)) {
                    var inserted = readRanges.add(new Range(startDesired - nGhosts, endDesired + nGhosts));
                    assert inserted;
                }
                return ok;
            case WriteOnly:
                if (ok = isWriteOk(startDesired, endDesired)) {
                    var inserted = writeRanges.add(new Range(startDesired, endDesired));
                    assert inserted;
                }
                return ok;
            case ReadWrite:
                if (ok = isReadOk(startDesired - nGhosts, endDesired + nGhosts) && isWriteOk(startDesired, endDesired)) {
                    var inserted = readRanges.add(new Range(startDesired - nGhosts, endDesired + nGhosts));
                    inserted &= writeRanges.add(new Range(startDesired, endDesired));
                    assert inserted;
                }
                return ok;
            default:
                throw new IllegalArgumentException("Invalid intent kind: " + kind);
        }
    }

    public void release(PartIntentKind kind, int startDesired, int endDesired, int nGhosts) {
        boolean removed;
        switch (kind) {
            case ReadOnly:
                removed = readRanges.remove(new Range(startDesired - nGhosts, endDesired + nGhosts));
                assert removed;
                break;
            case WriteOnly:
                removed = writeRanges.remove(new Range(startDesired, endDesired));
                assert removed;
                break;
            case ReadWrite:
                removed = readRanges.remove(new Range(startDesired - nGhosts, endDesired + nGhosts));
                removed &= writeRanges.remove(new Range(startDesired, endDesired));
                assert removed;
                break;
        }
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
        if (!isReadOk(startDesired, endDesired)) {
            return false;
        }

        for (var range : readRanges) {
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
