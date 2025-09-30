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

    public static GuardSet of(Guard... guards) {
        Guard[] sorted;

        if (guards == null || guards.length == 0) {
            return EMPTY;
        } else if (guards.length == 1) {
            return new GuardSet(guards);
        } else if (guards.length == 2) {
            if (guards[0].compareTo(guards[1]) <= 0) {
                sorted = guards;
            } else {
                sorted = new Guard[] {guards[1], guards[0]};
            }
        } else if (guards.length == 3) {
            if (guards[0].compareTo(guards[1]) <= 0) {
                if (guards[1].compareTo(guards[2]) <= 0) {
                    sorted = guards;
                } else {
                    if (guards[0].compareTo(guards[2]) <= 0) {
                        sorted = new Guard[] {guards[0], guards[2], guards[1]};
                    } else {
                        sorted = new Guard[] {guards[2], guards[0], guards[1]};
                    }
                }
            } else {
                if (guards[1].compareTo(guards[2]) <= 0) {
                    if (guards[0].compareTo(guards[2]) <= 0) {
                        sorted = new Guard[] {guards[1], guards[0], guards[2]};
                    } else {
                        sorted = new Guard[] {guards[1], guards[2], guards[0]};
                    }
                } else {
                    sorted = new Guard[] {guards[2], guards[1], guards[0]};
                }
            }
        } else {
            sorted = guards.clone();
            Arrays.sort(sorted);
        }

        return new GuardSet(moveDuplicatesToEnd(sorted), sorted);
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
