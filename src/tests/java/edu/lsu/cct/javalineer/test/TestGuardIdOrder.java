package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardSet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guards are acquired in the order of their ids, and deadlock freedom rests on that order being total. Ids came from a
 * 32-bit counter and compareTo subtracted them, which is a consistent order only while every live guard's id lies
 * within 2^31 of every other's. A guard made early (x) that is still alive when the counter has passed 2^31 (y, at the
 * boundary) and gone on another 2^30 (z) makes a cycle, x &lt; y &lt; z &lt; x, and three acquisitions of {x, y},
 * {y, z} and {z, x} then each take the guard another one waits for.
 * <p>
 * The test moves Guard's id counter forward by reflection instead of making 3 billion guards, so it needs no hook in
 * Guard. Before the fix the order checks fail and the three acquisitions deadlock (reported after a timeout); after
 * it, ids are 64-bit, the order is total, and the three acquisitions complete.
 */
public class TestGuardIdOrder {
    static final long TIMEOUT_SECONDS = 10;
    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static synchronized void check(boolean b, String what) {
        checks++;
        if (!b) {
            failures.add(what);
        }
    }

    /** Sets Guard's id counter, whichever width it has: the next guard made gets id v (v mod 2^32 for 32 bits). */
    static void setNextId(long v) throws Exception {
        Field f = Guard.class.getDeclaredField("nextId");
        f.setAccessible(true);
        Object counter = f.get(null);
        if (counter instanceof AtomicLong) {
            ((AtomicLong) counter).set(v);
        } else if (counter instanceof AtomicInteger) {
            ((AtomicInteger) counter).set((int) v);
        } else {
            throw new AssertionError("Guard.nextId is a " + counter.getClass());
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();

        var x = new Guard();                       // a long-lived guard, made early
        setNextId(Integer.MAX_VALUE);              // the counter at the 32-bit boundary
        var y = new Guard();
        setNextId(3L << 30);                       // 2^30 past the boundary
        var z = new Guard();

        // The order must be the order of creation, and in particular transitive.
        check(x.compareTo(y) < 0, "x < y");
        check(y.compareTo(z) < 0, "y < z");
        check(x.compareTo(z) < 0, "x < z (transitivity)");
        check(z.compareTo(x) > 0 && y.compareTo(x) > 0 && z.compareTo(y) > 0, "antisymmetry");
        check(x.compareTo(x) == 0 && !x.equals(y) && !y.equals(z) && !x.equals(z), "reflexive, ids distinct");
        var xyz = GuardSet.of(z, x, y);
        check(xyz.get(0) == x && xyz.get(1) == y && xyz.get(2) == z, "a set of the three is in creation order");
        check(xyz.contains(x) && xyz.contains(y) && xyz.contains(z), "the set finds each of its guards");

        // This thread holds x, y and z (each body is inline: the guards are free) while the three acquisitions are
        // queued; then it releases z, y and x in that order. With a cyclic order, {z, x} is queued on z, {y, z} on y
        // and {x, y} on x: each gets its first guard and waits for the next, held by another of them.
        var futs = new ArrayList<CompletableFuture<Void>>();
        var ran = new AtomicInteger();
        x.runGuarded(() -> y.runGuarded(() -> z.runGuarded(() -> {
            futs.add(Guard.runGuarded(GuardSet.of(x, y), ran::incrementAndGet));
            futs.add(Guard.runGuarded(GuardSet.of(y, z), ran::incrementAndGet));
            futs.add(Guard.runGuarded(GuardSet.of(z, x), ran::incrementAndGet));
        }))).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(futs.size() == 3, "the three acquisitions were submitted");
        try {
            for (var f : futs) {
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (TimeoutException e) {
            check(false, "the acquisitions of {x, y}, {y, z} and {z, x} deadlocked (" + ran.get() + " of 3 ran)");
        }
        check(ran.get() == 3 || !failures.isEmpty(), "all three bodies ran");

        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("TestGuardIdOrder passed (" + checks + " checks)");
        System.exit(0);
    }
}
