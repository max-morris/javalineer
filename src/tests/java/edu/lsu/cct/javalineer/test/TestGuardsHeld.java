package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The guards a thread holds (Guard.has, and through it Var.get's check) are those of the body it is running, and
 * after a body nested inside another ends, the enclosing body's again. A nested acquisition of a free guard runs its
 * body inline, inside the enclosing body: before the fix, its guards stayed recorded as held when it ended, so the
 * enclosing body lost access to its own variables (Var.get's assertion failed), and once a body ended, its thread
 * went on reporting its guards as held.
 */
public class TestGuardsHeld {
    static final long TIMEOUT_SECONDS = 30;
    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    // Called from inside bodies, where an exception would only fail the body's future: record and check later.
    static synchronized void check(boolean b, String what) {
        checks++;
        if (!b) {
            failures.add(what);
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var a = new GuardVar<>(1);
        var b = new GuardVar<>(2);
        var c = new GuardVar<>(3);

        // A single-guard acquisition nested in a single-guard body
        Guard.runGuarded(a, av -> {
            Guard.runGuarded(b, bv -> {
                check(bv.get() == 2, "the nested body reads b");
            });
            check(Guard.has(a), "a is held again after the nested body");
            check(!Guard.has(b), "b is not held after the nested body");
            check(av.get() == 1, "a's body still reads a");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // A multi-guard acquisition nested in a single-guard body
        Guard.runGuarded(a, av -> {
            Guard.runGuarded(b, c, (bv, cv) -> {
                check(bv.get() + cv.get() == 5, "the nested body reads b and c");
            });
            check(Guard.has(a) && !Guard.has(b) && !Guard.has(c), "only a is held after the nested body");
            check(av.get() == 1, "a's body still reads a");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // A single-guard acquisition nested in a multi-guard body
        Guard.runGuarded(a, b, (av, bv) -> {
            Guard.runGuarded(c, cv -> {
                check(cv.get() == 3, "the nested body reads c");
            });
            check(Guard.has(a) && Guard.has(b) && !Guard.has(c), "a and b are held after the nested body");
            check(av.get() + bv.get() == 3, "the body still reads a and b");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The bodies above ran on this thread (every guard was free): none is held after them
        check(!Guard.has(a) && !Guard.has(b) && !Guard.has(c), "no guard is held after the bodies end");

        if (!failures.isEmpty()) {
            throw new AssertionError("FAILED: " + failures);
        }
        System.out.println("TestGuardsHeld passed (" + checks + " checks)");
        System.exit(0);
    }
}
