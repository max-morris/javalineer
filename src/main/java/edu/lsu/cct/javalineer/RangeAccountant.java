package edu.lsu.cct.javalineer;

import java.util.*;
import java.util.Set;

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
                    var inserted = writeRanges.add(new Range(startDesired, endDesired));
                    assert inserted;
                }
                return ok;
            case ReadWrite:
                if (ok = isReadOk(startDesired - nGhosts, endDesired + nGhosts) && isWriteOk(startDesired, endDesired)) {
                    insertRead(new Range(startDesired - nGhosts, endDesired + nGhosts));
                    var inserted = writeRanges.add(new Range(startDesired, endDesired));
                    assert inserted;
                }
                return ok;
            default:
                throw new IllegalArgumentException("Invalid intent kind: " + kind);
        }
    }

    public void release(PartIntentKind kind, int startDesired, int endDesired, int nGhosts) {
        boolean removed;
        Range rr;
        switch (kind) {
            case ReadOnly:
                rr = new Range(startDesired - nGhosts, endDesired + nGhosts);
                assert readRanges.containsKey(rr);
                removeRead(rr);
                break;
            case WriteOnly:
                removed = writeRanges.remove(new Range(startDesired, endDesired));
                assert removed;
                break;
            case ReadWrite:
                rr = new Range(startDesired - nGhosts, endDesired + nGhosts);
                assert readRanges.containsKey(rr);
                removeRead(rr);
                removed = writeRanges.remove(new Range(startDesired, endDesired));
                assert removed;
                break;
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
