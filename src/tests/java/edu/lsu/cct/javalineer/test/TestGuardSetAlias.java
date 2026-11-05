package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A GuardSet is immutable and sorted, whatever its caller does with the array it was made from. GuardSet.of kept the
 * caller's array when it had one element, or two or three already in order, so writing to the array afterward
 * reordered the set (or replaced its guard), and of(g, g) wrote a null into the caller's array while removing the
 * duplicate. A set reordered so is acquired in descending order: against an ordinary acquisition of the same two
 * guards, each takes the guard the other waits for, and both wait forever. Before the fix that deadlock is reported
 * after a timeout; after it, both acquisitions complete.
 */
public class TestGuardSetAlias {
    static final long TIMEOUT_SECONDS = 10;
    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static synchronized void check(boolean b, String what) {
        checks++;
        if (!b) {
            failures.add(what);
        }
    }

    /** The set's guards, by get and by the iterator, which must agree; also checks the iterator's end and get's bounds. */
    static List<Guard> contents(GuardSet s) {
        var byGet = new ArrayList<Guard>();
        for (int i = 0; i < s.size(); i++) {
            byGet.add(s.get(i));
        }
        var byIter = new ArrayList<Guard>();
        var it = s.iterator();
        while (it.hasNext()) {
            byIter.add(it.next());
        }
        check(byGet.equals(byIter), "get and the iterator agree on " + byGet);
        boolean ended = false;
        try {
            it.next();
        } catch (NoSuchElementException e) {
            ended = true;
        }
        check(ended, "the iterator of " + byGet + " ends");
        for (int idx : new int[] {-1, s.size()}) {
            boolean thrown = false;
            try {
                s.get(idx);
            } catch (IndexOutOfBoundsException e) {
                thrown = true;
            }
            check(thrown, "get(" + idx + ") of " + byGet + " is out of bounds");
        }
        return byGet;
    }

    /**
     * Sets of zero to five guards. Up to three are kept in fields and more in an array of the set's own, so each size
     * is checked for its order, its independence from the caller's array, get, the iterator, contains, union, equals
     * and hashCode; and a large array whose duplicates leave three or fewer guards.
     */
    static void checkEachSize(Guard a, Guard b, Guard c) {
        var d = new Guard();
        var e = new Guard();
        var other = new Guard();
        var all = List.of(a, b, c, d, e);

        check(GuardSet.of().size() == 0 && GuardSet.of((Guard[]) null).size() == 0, "of() and of(null) are empty");
        check(contents(GuardSet.of()).isEmpty(), "the empty set has no guards");
        check(!GuardSet.of().contains(a), "the empty set contains nothing");

        for (int n = 1; n <= 5; n++) {
            var want = all.subList(0, n);
            // Descending, so of sorts every size; then every element of the array is overwritten.
            var arr = new Guard[n];
            for (int i = 0; i < n; i++) {
                arr[i] = want.get(n - 1 - i);
            }
            var s = GuardSet.of(arr);
            Arrays.fill(arr, other);
            check(s.size() == n && contents(s).equals(want), "of, size " + n + ", is " + want + " after its array changes");
            for (var g : all) {
                check(s.contains(g) == want.contains(g), "size " + n + " contains " + g + " iff it is one of " + want);
            }
            check(!s.contains(other), "size " + n + " does not contain a guard written to its array afterward");
            var again = GuardSet.of(want.toArray(new Guard[0]));
            check(s.equals(again) && s.hashCode() == again.hashCode(), "two sets of " + want + " are equal");
            check(!s.equals(GuardSet.of(other)), "size " + n + " differs from a set of one other guard");

            // Unions over every split of the size, the halves sharing one guard, across small and large sets.
            for (int split = 0; split <= n; split++) {
                var lo = GuardSet.of(want.subList(0, Math.min(n, split + 1)).toArray(new Guard[0]));
                var hi = GuardSet.of(want.subList(split, n).toArray(new Guard[0]));
                check(contents(lo.union(hi)).equals(want) && contents(hi.union(lo)).equals(want),
                      "the union of " + contents(lo) + " and " + contents(hi) + " is " + want);
            }
        }

        // A large array whose duplicates leave three guards, or one, gives a small set; the array is unchanged.
        var dupArr = new Guard[] {c, a, b, a, c, b};
        check(contents(GuardSet.of(dupArr)).equals(List.of(a, b, c)), "of(c, a, b, a, c, b) is [a, b, c]");
        check(Arrays.equals(dupArr, new Guard[] {c, a, b, a, c, b}), "of(c, a, b, a, c, b) leaves its array alone");
        check(contents(GuardSet.of(b, b, b, b)).equals(List.of(b)), "of(b, b, b, b) is [b]");
        check(contents(GuardSet.of(b, b, b)).equals(List.of(b)), "of(b, b, b) is [b]");
        check(contents(GuardSet.of(c, b, b)).equals(List.of(b, c)), "of(c, b, b) is [b, c]");
        check(contents(GuardSet.of(b, c, b)).equals(List.of(b, c)), "of(b, c, b) is [b, c]");
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var a = new Guard();
        var b = new Guard();
        var c = new Guard();

        // One element: the set keeps its guard.
        var one = new Guard[] {a};
        var s1 = GuardSet.of(one);
        one[0] = b;
        check(s1.size() == 1 && s1.get(0) == a, "a one-guard set keeps its guard after its array changes");

        // Two and three elements already in order: the set keeps its order.
        var two = new Guard[] {a, b};
        var s2 = GuardSet.of(two);
        two[0] = b;
        two[1] = a;
        check(s2.get(0) == a && s2.get(1) == b, "a two-guard set keeps its order after its array changes");
        var three = new Guard[] {a, b, c};
        var s3 = GuardSet.of(three);
        three[0] = c;
        three[2] = a;
        check(s3.get(0) == a && s3.get(1) == b && s3.get(2) == c,
              "a three-guard set keeps its order after its array changes");

        // Removing a duplicate leaves the caller's array alone.
        var dup = new Guard[] {a, a};
        var sd = GuardSet.of(dup);
        check(sd.size() == 1 && sd.get(0) == a, "of(a, a) is {a}");
        check(dup[0] == a && dup[1] == a, "of(a, a) leaves its array as it was");

        // Every order of two and three guards, with and without a duplicate, sorts to the same set (the sort now
        // works in place on the copy).
        Guard[][] orders = {{a, b}, {b, a}, {a, b, c}, {a, c, b}, {b, a, c}, {b, c, a}, {c, a, b}, {c, b, a}};
        for (var order : orders) {
            var s = GuardSet.of(order);
            check(s.get(0) == a && s.get(1) == b && (order.length == 2 ? s.size() == 2 : s.get(2) == c && s.size() == 3),
                  "of" + java.util.Arrays.toString(order) + " is sorted");
        }
        Guard[][] dups = {{b, a, b}, {b, b, a}, {a, b, a}, {b, a, a, b}, {c, a, b, a, c}};
        for (var order : dups) {
            var s = GuardSet.of(order);
            boolean hasC = order.length == 5;
            check(s.size() == (hasC ? 3 : 2) && s.get(0) == a && s.get(1) == b && (!hasC || s.get(2) == c),
                  "of" + java.util.Arrays.toString(order) + " is sorted without duplicates");
        }

        checkEachSize(a, b, c);

        // ofCached caches by the array's identity: after a write to the array it must give the set of the guards the
        // array holds now, not the one it held before.
        var cachedArr = new Guard[] {a, b};
        var c1 = GuardSet.ofCached(cachedArr);
        check(GuardSet.ofCached(cachedArr) == c1, "ofCached hits for the same array, unchanged");
        cachedArr[1] = c;
        var c2 = GuardSet.ofCached(cachedArr);
        check(c2.size() == 2 && c2.get(0) == a && c2.get(1) == c, "ofCached after a write to its array holds a and c");
        check(c1.get(0) == a && c1.get(1) == b, "the earlier cached set is unchanged");

        // The deadlock. This thread holds a and then b (inline: both are free). Inside, a set made from a sorted
        // array which is then reversed is queued on its first guard, and an ordinary acquisition of the same two
        // guards on a. Releasing b hands it to the reversed set, which then waits for a behind the ordinary one;
        // releasing a hands a to the ordinary one, which then waits for b.
        var arr = new Guard[] {a, b};
        var reversed = GuardSet.of(arr);
        arr[0] = b;
        arr[1] = a;
        var futs = new ArrayList<CompletableFuture<Void>>();
        var ran = new AtomicInteger();
        a.runGuarded(() -> b.runGuarded(() -> {
            futs.add(Guard.runGuarded(reversed, ran::incrementAndGet));
            futs.add(Guard.runGuarded(GuardSet.of(a, b), ran::incrementAndGet));
        })).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            for (var f : futs) {
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (TimeoutException e) {
            check(false, "a set whose array was reversed after GuardSet.of deadlocked against {a, b} ("
                    + ran.get() + " of 2 ran)");
        }

        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("TestGuardSetAlias passed (" + checks + " checks)");
        System.exit(0);
    }
}
