package edu.lsu.cct.javalineer;

import java.util.*;
import java.util.Set;

/**
 * The ranges of a PartitionableList held by partitioned calls: read ranges counted, write ranges each held once. An
 * empty (or inverted) write range writes nothing, conflicts with nothing and is not recorded, so that two calls on an
 * empty list (or parts with no writable cells) can hold their write ranges at once.
 * <p>
 * Two ranges conflict when one of them is a write range and they overlap: a write range conflicts with every read and
 * write range it overlaps, and two read ranges never conflict. A request (a kind and a range, from {@link PartCall})
 * conflicts with another request when either one, held, would refuse the other.
 * <p>
 * <b>Admission policy.</b> Every request is checked under the list's guard, when it is made and again after every
 * release, until it is admitted (PartCall.admitFrom). A request is <i>queued</i> from its first refused check
 * until it is admitted. There are two policies, chosen once per JVM by the system property
 * {@code JAVALINEER_FAIR_RANGES} (read into {@link #FAIR}):
 * <ul>
 * <li><b>Reader preference</b> (the default, {@code JAVALINEER_FAIR_RANGES} unset or not {@code true}): a request is
 * refused only by a held range that conflicts with it. A queued request delays nobody, so a read is admitted while a
 * conflicting write is queued, and a write on a list that is read without a pause can be delayed forever.</li>
 * <li><b>Fair admission</b> ({@code -DJAVALINEER_FAIR_RANGES=true}): a request is refused by a held range that
 * conflicts with it, or by a request queued before it that conflicts with it. Requests that conflict are therefore
 * admitted in the order in which they were made (in which their first checks ran), per list. Requests that do not
 * conflict never delay each other: reads share with reads, and a write delays only the requests that overlap it.
 * <br>The promise: a queued request is admitted once every range that was held, and every conflicting request that
 * was queued, when its first check ran has been admitted and released. That set is fixed when the request is made, so
 * requests made later cannot delay it, however many there are.
 * <br>The price: a request cannot pass a conflicting request queued before it, even when the same program holds a
 * range that the queued request waits for. A call that keeps a read range until a future completes (the parts of
 * reducePartitioned keep it until their results are in) and whose future waits for a new read of the same list never
 * completes if a write of that list is queued between the two: the write waits for the kept range, the new read waits
 * for the write, and the kept range waits for the new read. Under reader preference the new read passes the write.
 * Guardian rejects such programs statically.</li>
 * </ul>
 */
public class RangeAccountant {
    /**
     * Fair admission (see the class comment), from {@code -DJAVALINEER_FAIR_RANGES=true}; reader preference otherwise.
     * Read once, so that under reader preference PartCall's test of it is a constant the JIT folds away, and the
     * check is {@link #isRangeOk(PartIntentKind, int, int, int)} as before.
     */
    static final boolean FAIR =
            "true".equalsIgnoreCase(System.getProperty("JAVALINEER_FAIR_RANGES", "false").strip());

    private final Map<Range, Integer> readRanges = new HashMap<>();
    private final Set<Range> writeRanges = new HashSet<>();
    /**
     * Under fair admission, the queued requests in the order in which their first checks ran: null until a check is
     * first refused, and always null under reader preference. Read and written only under the list's guard, like the
     * ranges. A request is appended in the body of its first refused check and removed in the body of the check that
     * admits it, which is also when the condition adds it to and removes it from its waiting tasks (CondContext), so
     * the two lists hold the same requests in the same order, and a signalAll checks them in this order.
     */
    private ArrayList<Queued> queued;

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

    /**
     * Fair admission's check (PartCall uses it only when {@link #FAIR} is set): checks a request of {@code requester}
     * (one per call: a call has at most one request queued at a time, since it admits its lists one after another)
     * and, if it is admitted, takes its ranges. The request is refused while a request queued before it conflicts with
     * it, or a held range does; at its first refusal it is queued, and when it is admitted it leaves the queue.
     * {@code isQueued} says whether this request was refused before (is queued): the caller remembers it, so that a
     * check of a queued request stops at the first earlier request that conflicts with it, and a first check does not
     * look for an entry it cannot have.
     */
    boolean isRangeOkFair(Object requester, boolean isQueued, PartIntentKind kind, int startDesired, int endDesired,
                          int nGhosts) {
        var q = queued;
        int n = q == null ? 0 : q.size();
        // Only the requests queued before this one count: the scan stops at this request's own entry, if it is queued
        int i = 0;
        for (; i < n; i++) {
            var w = q.get(i);
            if (w.requester == requester) {
                break;
            }
            if (w.conflicts(kind, startDesired, endDesired, nGhosts)) {
                if (!isQueued) {
                    q.add(new Queued(requester, kind, startDesired, endDesired, nGhosts));
                }
                return false;
            }
        }
        assert isQueued == (i < n) : "a request's queued state and the queue disagree";
        // No request queued before this one conflicts with it: the held ranges decide, as under reader preference
        if (isRangeOk(kind, startDesired, endDesired, nGhosts)) {
            if (isQueued) {
                q.remove(i);
            }
            return true;
        }
        if (!isQueued) {
            if (q == null) {
                queued = q = new ArrayList<>();
            }
            q.add(new Queued(requester, kind, startDesired, endDesired, nGhosts));
        }
        return false;
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

    // The overlap test of isReadOk and isWriteOk, for two ranges given by their ends.
    private static boolean overlaps(int start1, int end1, int start2, int end2) {
        return start1 < end2 && end1 > start2;
    }

    /** A queued request (fair admission only): what it would hold, and who made it. */
    private static final class Queued {
        final Object requester;
        // The read range, if the request reads (ReadOnly, ReadWrite): [readStart, readEnd), ghosts included
        final boolean reads;
        final int readStart, readEnd;
        // The write range, if the request writes (WriteOnly, ReadWrite) a non-empty range: [writeStart, writeEnd)
        final boolean writes;
        final int writeStart, writeEnd;

        Queued(Object requester, PartIntentKind kind, int start, int end, int nGhosts) {
            this.requester = requester;
            this.reads = kind != PartIntentKind.WriteOnly;
            this.readStart = start - nGhosts;
            this.readEnd = end + nGhosts;
            this.writes = kind != PartIntentKind.ReadOnly && start < end;
            this.writeStart = start;
            this.writeEnd = end;
        }

        /**
         * Whether a request (kind, [start, end), nGhosts) conflicts with this one: whether either, held, would refuse
         * the other, by the tests of isReadOk and isWriteOk. A read range against this request's write range, and a
         * non-empty write range against this request's write and read ranges.
         */
        boolean conflicts(PartIntentKind kind, int start, int end, int nGhosts) {
            if (kind != PartIntentKind.WriteOnly && writes
                && overlaps(start - nGhosts, end + nGhosts, writeStart, writeEnd)) {
                return true;
            }
            if (kind != PartIntentKind.ReadOnly && start < end) {
                return (writes && overlaps(start, end, writeStart, writeEnd))
                       || (reads && overlaps(start, end, readStart, readEnd));
            }
            return false;
        }
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
