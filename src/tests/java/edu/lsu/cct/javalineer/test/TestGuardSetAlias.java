package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;

import java.util.ArrayList;
import java.util.List;
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
