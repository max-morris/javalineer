package edu.lsu.cct.javalineer;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An immutable, sorted collection of {@linkplain Guard Guards}.
 *
 * <p>A set of at most three guards keeps them in the fields {@code g0}, {@code g1} and {@code g2} and has no array;
 * a larger set keeps them in an array of its own. Either way the set never holds an array anyone else can reach:
 * an acquisition takes its guards in the set's order, and deadlock freedom rests on that order being ascending and
 * fixed, so a caller writing to an array the set kept could reorder it (hole 67). Keeping the small sets in fields
 * also means that making one allocates nothing but the set itself: the copy of the caller's array that hole 67
 * otherwise needs costs 11 to 16 % on an uncontended two- or three-guard acquisition.
 */
public class GuardSet implements Iterable<Guard> {
    /** The most guards a set keeps in fields rather than in an array. */
    private static final int SMALL = 3;

    private final int size;
    /** The guards of a set of at most {@link #SMALL}, in order; null past {@code size}, and all null when large. */
    private final Guard g0, g1, g2;
    /** The guards of a set of more than {@link #SMALL}, sorted and the set's own; null for a small set. */
    private final Guard[] guards;

    private GuardSet(int size, Guard g0, Guard g1, Guard g2) {
        assert size <= SMALL;
        assert (size < 1 || g0 != null) && (size < 2 || g1 != null) && (size < 3 || g2 != null);
        this.size = size;
        this.g0 = g0;
        this.g1 = g1;
        this.g2 = g2;
        this.guards = null;
    }

    private GuardSet(int size, Guard[] guards) {
        assert size > SMALL;
        this.size = size;
        this.g0 = null;
        this.g1 = null;
        this.g2 = null;
        this.guards = guards;
    }

    /**
     * The set of guards, sorted, without duplicates. The set never shares the caller's array, so writing to the array
     * afterward cannot reorder the set or change its guards, and removing a duplicate does not write into it. One to
     * three guards are read from the array once each and sorted as local values, with no copy; more are copied and
     * the copy is sorted. Either way a thread writing to the array while this runs cannot leave the set unsorted.
     */
    public static GuardSet of(Guard... guards) {
        if (guards == null) {
            return EMPTY;
        }
        switch (guards.length) {
            case 0:
                return EMPTY;
            case 1:
                return of1(guards[0]);
            case 2:
                return of2(guards[0], guards[1]);
            case 3:
                return of3(guards[0], guards[1], guards[2]);
            default:
                return ofLarge(guards.clone());
        }
    }

    /**
     * As {@link #of}, but an array of more than three guards becomes the set's own, sorted in place: only for an
     * array no one else can reach, such as the varargs array the compiler makes at a call {@code ofUnshared(g1, g2,
     * g3, g4)}. It saves the copy on the multi-guard acquisitions of GuardVars, which build a set on every call. Up to
     * three guards, it is {@link #of}: those are never kept in an array.
     */
    static GuardSet ofUnshared(Guard... guards) {
        switch (guards.length) {
            case 0:
                return EMPTY;
            case 1:
                return of1(guards[0]);
            case 2:
                return of2(guards[0], guards[1]);
            case 3:
                return of3(guards[0], guards[1], guards[2]);
            default:
                return ofLarge(guards);
        }
    }

    /**
     * The two-guard set, with no array at all. The generated two-GuardVar acquisitions call {@code ofUnshared(gv1,
     * gv2)}, which resolves to this overload rather than the varargs one, so they build no varargs array either.
     */
    static GuardSet ofUnshared(Guard a, Guard b) {
        return of2(a, b);
    }

    /** The three-guard set, with no array at all; as {@link #ofUnshared(Guard, Guard)}, for three GuardVars. */
    static GuardSet ofUnshared(Guard a, Guard b, Guard c) {
        return of3(a, b, c);
    }

    private static GuardSet of1(Guard a) {
        // The two- and three-guard forms fail on a null at once through compareTo; so does this one.
        java.util.Objects.requireNonNull(a, "guard");
        return new GuardSet(1, a, null, null);
    }

    private static GuardSet of2(Guard a, Guard b) {
        int ab = a.compareTo(b);
        if (ab < 0) {
            return new GuardSet(2, a, b, null);
        } else if (ab > 0) {
            return new GuardSet(2, b, a, null);
        } else {
            return of1(a);
        }
    }

    private static GuardSet of3(Guard a, Guard b, Guard c) {
        // Sort the three local values: the caller's array, if any, is not read again.
        Guard t;
        if (a.compareTo(b) > 0) {
            t = a;
            a = b;
            b = t;
        }
        if (b.compareTo(c) > 0) {
            t = b;
            b = c;
            c = t;
            if (a.compareTo(b) > 0) {
                t = a;
                a = b;
                b = t;
            }
        }
        // a <= b <= c: drop the duplicates.
        if (a.compareTo(b) == 0) {
            return of2(a, c);
        } else if (b.compareTo(c) == 0) {
            return new GuardSet(2, a, b, null);
        }
        return new GuardSet(3, a, b, c);
    }

    /** The set of the guards of an array which becomes the set's own (or garbage), sorted in place. */
    private static GuardSet ofLarge(Guard[] guards) {
        Arrays.sort(guards);
        return ofSorted(guards, moveDuplicatesToEnd(guards));
    }

    /** The set of the first n guards of a sorted array without duplicates, which becomes the set's own (or garbage). */
    private static GuardSet ofSorted(Guard[] sorted, int n) {
        switch (n) {
            case 0:
                return EMPTY;
            case 1:
                return new GuardSet(1, sorted[0], null, null);
            case 2:
                return new GuardSet(2, sorted[0], sorted[1], null);
            case 3:
                return new GuardSet(3, sorted[0], sorted[1], sorted[2]);
            default:
                return new GuardSet(n, sorted);
        }
    }

    /**
     * As {@link #of}, through a per-thread cache keyed by the array itself (by identity), for a caller which passes
     * the same array again and again. A hit is checked against the array's contents when the set was made: if the
     * caller has written to the array since, the set is made again, so the result is always the set of the guards
     * the array holds now, never those it held before (a stale set would run a body without a guard it names).
     */
    public static GuardSet ofCached(Guard... guards) {
        if (guards == null || guards.length == 0) {
            return EMPTY;
        }
        var cache = CACHE.get();

        var ref = cache.get(guards);
        if (ref != null) {
            var cached = ref.get();
            if (cached != null && cached.madeFrom(guards)) {
                return cached.set;
            }
        }

        // The set is made from the copy, so that the contents checked on a hit are exactly those the set holds even
        // if another thread writes to the array meanwhile. of keeps neither array.
        var contents = guards.clone();
        var entry = new Cached(contents, of(contents));
        cache.put(guards, new SoftReference<>(entry));

        return entry.set;
    }

    /** A cached set and the contents of the array it was made from. */
    private static final class Cached {
        final Guard[] contents;
        final GuardSet set;

        Cached(Guard[] contents, GuardSet set) {
            this.contents = contents;
            this.set = set;
        }

        boolean madeFrom(Guard[] guards) {
            if (guards.length != contents.length) {
                return false;
            }
            for (int i = 0; i < guards.length; i++) {
                if (guards[i] != contents[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final ThreadLocal<HashMap<Guard[], SoftReference<Cached>>> CACHE =
            ThreadLocal.withInitial(HashMap::new);
    private static final GuardSet EMPTY = new GuardSet(0, null, null, null);

    /** The guard at idx, which is less than size. */
    private Guard at(int idx) {
        Guard[] gs = guards;
        if (gs != null) {
            return gs[idx];
        }
        return idx == 0 ? g0 : idx == 1 ? g1 : g2;
    }

    public GuardSet union(GuardSet other) {
        // Both sets are sorted and without duplicates, so a merge which takes a guard in both once is too.
        int i = 0, j = 0, k = 0;
        Guard[] sorted = new Guard[this.size + other.size];

        while (i < this.size && j < other.size) {
            Guard x = this.at(i), y = other.at(j);
            int c = x.compareTo(y);
            if (c <= 0) {
                sorted[k++] = x;
                i++;
                if (c == 0) {
                    j++;
                }
            } else {
                sorted[k++] = y;
                j++;
            }
        }
        while (i < this.size) {
            sorted[k++] = this.at(i++);
        }
        while (j < other.size) {
            sorted[k++] = other.at(j++);
        }

        return ofSorted(sorted, k);
    }

    @Override
    public Iterator<Guard> iterator() {
        return new Iterator<>() {
            private int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < size;
            }

            @Override
            public Guard next() {
                if (pos >= size) {
                    throw new NoSuchElementException();
                }
                return at(pos++);
            }
        };
    }

    public int size() {
        return size;
    }

    public Guard get(int idx) {
        if (idx < 0 || idx >= size) {
            throw new IndexOutOfBoundsException("Guard index " + idx + " out of bounds.");
        }

        return at(idx);
    }

    public boolean contains(Guard guard) {
        if (guards != null) {
            return Arrays.binarySearch(guards, 0, size, guard) >= 0;
        }
        for (int i = 0; i < size; i++) {
            if (at(i).compareTo(guard) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Two sets are equal when they hold the same guards (in the same order, since both are sorted). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardSet other = (GuardSet) o;
        if (size != other.size) return false;
        for (int i = 0; i < size; i++) {
            if (!at(i).equals(other.at(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = size;
        for (int i = 0; i < size; i++) {
            h = 31 * h + at(i).hashCode();
        }
        return h;
    }

    private static void swap(Guard[] guards, int i, int j) {
        Guard tmp = guards[i];
        guards[i] = guards[j];
        guards[j] = tmp;
    }

    private static int moveDuplicatesToEnd(Guard[] guards) {
        Guard g = null;
        int duplicates = 0;

        for (int i = 0; i < guards.length; i++) {
            assert guards[i] != null;
            if (guards[i].equals(g)) {
                guards[i] = null;
                duplicates++;
            } else {
                g = guards[i];
            }
        }

        if (duplicates > 0) {
            for (int i = 0, j = 0; i < guards.length; i++) {
                if (guards[j] == null) {
                    if (guards[i] != null) {
                        swap(guards, i, j++);
                    }
                } else {
                    j++;
                }
            }
        }

        return guards.length - duplicates;
    }
}
