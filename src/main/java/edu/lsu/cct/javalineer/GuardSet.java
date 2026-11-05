package edu.lsu.cct.javalineer;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

/**
 * An immutable, sorted collection of {@linkplain Guard Guards}.
 */
public class GuardSet implements Iterable<Guard> {
    private final int size;
    private final Guard[] guards;

    private GuardSet(int size, Guard... guards) {
        this.size = size;
        this.guards = guards;
    }

    private GuardSet(Guard... guards) {
        this(guards.length, guards);
    }

    /**
     * The set of guards, sorted, without duplicates. The set never shares the caller's array: it copies it first, so
     * writing to the array afterward cannot reorder the set or change its guards (an acquisition takes its guards in
     * the set's order, and a set reordered so could deadlock against an ordinary one), and removing a duplicate does
     * not write into the caller's array. Sorting the copy, not the original, also means a thread writing to the
     * array while this runs cannot leave the set unsorted.
     */
    public static GuardSet of(Guard... guards) {
        if (guards == null || guards.length == 0) {
            return EMPTY;
        }
        return ofUnshared(guards.clone());
    }

    /**
     * As {@link #of}, but the array becomes the set's own, sorted in place: only for an array no one else can reach,
     * such as the varargs array the compiler makes at a call {@code ofUnshared(g1, g2)}. It saves the copy on the
     * multi-guard acquisitions of GuardVars, which build a set on every call.
     */
    static GuardSet ofUnshared(Guard... guards) {
        if (guards.length == 0) {
            return EMPTY;
        } else if (guards.length == 2) {
            if (guards[0].compareTo(guards[1]) > 0) {
                swap(guards, 0, 1);
            }
        } else if (guards.length == 3) {
            if (guards[0].compareTo(guards[1]) > 0) {
                swap(guards, 0, 1);
            }
            if (guards[1].compareTo(guards[2]) > 0) {
                swap(guards, 1, 2);
                if (guards[0].compareTo(guards[1]) > 0) {
                    swap(guards, 0, 1);
                }
            }
        } else if (guards.length > 3) {
            Arrays.sort(guards);
        }

        return new GuardSet(moveDuplicatesToEnd(guards), guards);
    }

    public static GuardSet ofCached(Guard... guards) {
        var cache = CACHE.get();

        var ref = cache.get(guards);
        if (ref != null) {
            var cached = ref.get();
            if (cached != null) {
                return cached;
            }
        }

        var gSet = GuardSet.of(guards);
        cache.put(guards, new SoftReference<>(gSet));

        return gSet;
    }

    private static final ThreadLocal<HashMap<Guard[], SoftReference<GuardSet>>> CACHE =
            ThreadLocal.withInitial(HashMap::new);
    private static final GuardSet EMPTY = new GuardSet();

    public GuardSet union(GuardSet other) {
        int i = 0, j = 0, k = 0;
        Guard[] sorted = new Guard[this.size + other.size];

        while (i < this.size && j < other.size) {
            if (this.guards[i].compareTo(other.guards[j]) == 0) {
                if (k == 0 || this.guards[i].compareTo(sorted[k - 1]) != 0) {
                    sorted[k++] = this.guards[i];
                }
                i++;
                j++;
            } else if (this.guards[i].compareTo(other.guards[j]) < 0) {
                if (k == 0 || this.guards[i].compareTo(sorted[k - 1]) != 0) {
                    sorted[k++] = this.guards[i];
                }
                i++;
            } else {
                if (k == 0 || other.guards[j].compareTo(sorted[k - 1]) != 0) {
                    sorted[k++] = other.guards[j];
                }
                j++;
            }
        }

        while (i < this.size) {
            if (k == 0 || this.guards[i].compareTo(sorted[k - 1]) != 0) {
                sorted[k++] = this.guards[i];
            }
            i++;
        }

        while (j < other.size) {
            if (k == 0 || other.guards[j].compareTo(sorted[k - 1]) != 0) {
                sorted[k++] = other.guards[j];
            }
            j++;
        }

        return new GuardSet(k, sorted);
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
                return guards[pos++];
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

        return guards[idx];
    }

    public boolean contains(Guard guard) {
        return Arrays.binarySearch(guards, 0, size, guard) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GuardSet guards1 = (GuardSet) o;
        return size == guards1.size && Objects.deepEquals(guards, guards1.guards);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, Arrays.hashCode(guards));
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
