package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The guards a thread holds (Guard.has, and through it Var.get's check) are those of the body it is running, and
 * after a body nested inside another ends, the enclosing body's again. A nested acquisition of a free guard runs its
 * body inline, inside the enclosing body: before the fix, its guards stayed recorded as held when it ended, so the
 * enclosing body lost access to its own variables (Var.get's assertion failed), and once a body ended, its thread
 * went on reporting its guards as held. runGuardedEtAl adds to the guards of the enclosing body, so outside a body it
 * has nothing to add to and throws IllegalStateException.
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

        // A single-guard task is its own future, completed at the end of its body while its guards are still held:
        // a continuation registered while the task waits in the queue runs inside that completion and sees the
        // body's guards, whether the body returns or throws. (The held guards are restored after the completion.)
        var queued = new ArrayList<CompletableFuture<?>>();
        Guard.runGuarded(a, av -> {
            queued.add(Guard.runGuarded(a, av2 -> { }).thenRun(() -> {
                check(Guard.has(a), "a continuation of a queued task's future holds a");
                check(av.get() == 1, "a continuation of a queued task's future reads a");
            }));
            queued.add(Guard.runGuarded(a, av2 -> {
                throw new RuntimeException("expected by TestGuardsHeld");
            }).handle((v, t) -> {
                check(t != null && Guard.has(a), "a continuation of a failed queued task's future holds a");
                return null;
            }));
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (var f : queued) {
            f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // The bodies above ran on this thread (every guard was free): none is held after them
        check(!Guard.has(a) && !Guard.has(b) && !Guard.has(c), "no guard is held after the bodies end");

        // runGuardedEtAl inside a body acquires the body's guards together with its own: a is the enclosing body's,
        // so the acquisition waits for that body to end and then holds a, b and c.
        var etAl = new ArrayList<CompletableFuture<Void>>();
        Guard.runGuarded(a, av -> {
            etAl.add(Guard.runGuardedEtAl(b, c, (bv, cv) -> {
                check(Guard.has(a) && Guard.has(b) && Guard.has(c), "runGuardedEtAl's body holds a, b and c");
            }));
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        etAl.get(0).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Outside any body (this thread has run bodies, so before the fix it kept a stale set; after it, null)
        // runGuardedEtAl has nothing to add to: an IllegalStateException, not a NullPointerException.
        Throwable outside = null;
        try {
            Guard.runGuardedEtAl(b, c, (bv, cv) -> {
                check(false, "runGuardedEtAl outside a body ran its body");
            });
        } catch (Throwable t) {
            outside = t;
        }
        check(outside instanceof IllegalStateException,
              "runGuardedEtAl outside a body throws IllegalStateException (got " + outside + ")");

        if (!failures.isEmpty()) {
            throw new AssertionError("FAILED: " + failures);
        }
        System.out.println("TestGuardsHeld passed (" + checks + " checks)");
        System.exit(0);
    }
}
