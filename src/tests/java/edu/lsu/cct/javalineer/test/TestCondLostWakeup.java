package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;
import edu.lsu.cct.javalineer.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A signal must wake a waiting task whose predicate a write made true, wherever the signal is issued after the write,
 * as long as the signal's body runs after the write (see CondContext's class comment).
 * <p>
 * Each trial forces the one ordering that used to lose (Guardian proof notes, discrepancies.md hole 68): the waiter's
 * first check reads the state, then waits, still holding the condition's guards, until a writer thread has issued its
 * write and its signal; only then does the check return false. When the waiter list had a guard of its own, the
 * signal's snapshot of the list ran during that wait and found it empty, the waiter registered afterward, and nothing
 * ever ran it again: every trial of the losing modes below lost. With the list under the condition's guards, the
 * signal's body queues behind the check and finds the waiter registered.
 * <p>
 * Losing modes before the change (each must now wake in every trial):
 * <ul>
 * <li>after: {@code gv.runGuarded(v -> v.set(true)); cond.signal();} (hole 68 itself)
 * <li>afterAll: the same with signalAll
 * <li>hopAfter: the same with the signal handed to the pool, as Guardian's Guards.signal does on a deep stack
 * <li>twoCondAfter: two conditions on one guard; after the write, signal the other one, then this one
 * <li>subsetAfter: a condition on {gv, gw}; the write holds only gv
 * <li>queuedCheck: the first check is not inline but queued behind another body, and runs in its drain
 * <li>external: the predicate reads an atomic, and the writer sets it and signals outside any body. This one was
 *     known (guards-semantics.md section 4) and is covered too: the signal is issued after the write, so either the
 *     check's body came first and registered, or the check's body starts after the signal's and reads the new value.
 * </ul>
 * Modes which always woke, kept as controls: inside (write and signal in one body), thenRun (the signal in a
 * continuation of the write's future), hopInside (the pool hop issued inside the body), subsetInside.
 * <p>
 * What a signal does not claim, shown by the boundary case at the end: a write still queued when the signal's body
 * runs is not seen by it. A write on {ga, gc} whose lowest guard ga is not one of the condition's {gb, gc} is queued
 * on ga only, so a signal issued right after it can run first; that write needs a signal of its own.
 */
public class TestCondLostWakeup {
    static final int TRIALS = Integer.getInteger("TRIALS", 200);
    static final long WAIT_MS = 5_000;

    // Spins until the flag is set, failing the test after WAIT_MS (rather than hanging) if it never is.
    static void await(AtomicBoolean flag, String what) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (!flag.get()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("FAILED: timed out waiting for " + what);
            }
            Thread.onSpinWait();
        }
    }

    /** One trial; true if the waiter was woken. */
    static boolean trial(String mode) throws Exception {
        var gv = new GuardVar<Boolean>(false);
        var gw = new GuardVar<Boolean>(false);
        var flag = new AtomicBoolean(false);
        var started = new AtomicBoolean(false);   // the check has read the state
        var issued = new AtomicBoolean(false);    // the writer has issued its write and its signal
        boolean external = mode.equals("external");
        boolean subset = mode.startsWith("subset");

        Runnable writerBody;
        CompletableFuture<Void> f;
        Thread holder = null;
        var go = new AtomicBoolean(false);

        if (subset) {
            var cond = Guard.newCondition(gv, gw);
            writerBody = () -> {
                if (mode.equals("subsetInside")) {
                    gv.runGuarded(v -> { v.set(true); cond.signal(); });
                } else {
                    gv.runGuarded(v -> v.set(true));
                    cond.signal();
                }
                issued.set(true);
            };
            var writer = start(writerBody, started);
            f = Guard.runCondition(cond, (Var<Boolean> v, Var<Boolean> w) -> {
                boolean b = v.get();
                started.set(true);
                await(issued, "the writer");
                return b;
            });
            writer.join();
        } else {
            var cond = Guard.newCondition(gv);
            var other = Guard.newCondition(gv);
            writerBody = () -> {
                if (mode.equals("after") || mode.equals("queuedCheck")) {
                    gv.runGuarded(v -> v.set(true));
                    cond.signal();
                } else if (mode.equals("afterAll")) {
                    gv.runGuarded(v -> v.set(true));
                    cond.signalAll();
                } else if (mode.equals("external")) {
                    flag.set(true);
                    cond.signal();
                } else if (mode.equals("twoCondAfter")) {
                    gv.runGuarded(v -> v.set(true));
                    other.signal();
                    cond.signal();
                } else if (mode.equals("inside")) {
                    gv.runGuarded(v -> { v.set(true); cond.signal(); });
                } else if (mode.equals("thenRun")) {
                    Guard.runGuarded(gv, v -> v.set(true)).thenRun(cond::signal);
                } else if (mode.equals("hopInside")) {
                    gv.runGuarded(v -> { v.set(true); Pool.execute(cond::signal); });
                } else if (mode.equals("hopAfter")) {
                    gv.runGuarded(v -> v.set(true));
                    // The check waits until the hopped signal has been issued, so that it is the signal (not
                    // only its hand-off to the pool) which comes during the check.
                    Pool.execute(() -> { cond.signal(); issued.set(true); });
                    return;
                } else {
                    throw new IllegalArgumentException(mode);
                }
                issued.set(true);
            };
            if (mode.equals("queuedCheck")) {
                // Another thread holds gv in a body until the check is queued behind it; the check then runs in
                // that thread's drain, not inline on this one.
                var holding = new AtomicBoolean(false);
                holder = new Thread(() -> gv.runGuarded(v -> {
                    holding.set(true);
                    await(go, "the check to be queued");
                }));
                holder.start();
                await(holding, "the holder");
            }
            var writer = start(writerBody, started);
            f = Guard.runCondition(cond, (Var<Boolean> v) -> {
                boolean b = external ? flag.get() : v.get();
                started.set(true);
                await(issued, "the writer");
                return b;
            });
            go.set(true);
            if (holder != null) {
                holder.join();
            }
            writer.join();
        }
        try {
            f.get(2, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    static Thread start(Runnable writerBody, AtomicBoolean started) {
        var t = new Thread(() -> {
            await(started, "the check");
            writerBody.run();
        });
        t.start();
        return t;
    }

    /**
     * The boundary: the write's lowest guard is outside the condition's guards and is held, so the write is queued
     * when the signal issued after it runs. The signal reruns the waiter before the write: it stays waiting, as it
     * must, and a signal after the write has run wakes it. Deterministic, on one thread.
     */
    static void boundary() throws Exception {
        var ga = new GuardVar<>(0);   // made first, so the lowest of the three
        var gb = new GuardVar<>(0);
        var gc = new GuardVar<>(false);
        var cond = Guard.newCondition(gb, gc);
        var f = Guard.runCondition(cond, (Var<Integer> b, Var<Boolean> c) -> c.get());
        check(!f.isDone(), "boundary: the waiter waits");
        ga.runGuarded(a -> {
            Guard.runGuarded(ga, gc, (a2, c) -> c.set(true));   // queued on ga, held by this body
            cond.signal();                                      // runs now, before the write
        });
        var wrote = new boolean[1];
        Guard.runGuarded(gc, c -> { wrote[0] = c.get(); }).get(2, TimeUnit.SECONDS);
        check(wrote[0], "boundary: the write ran");
        check(!f.isDone(), "boundary: a signal whose body ran before the write leaves the waiter waiting");
        cond.signal();
        f.get(2, TimeUnit.SECONDS);
    }

    static void check(boolean b, String what) {
        if (!b) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        Pool.setTaskFailureHandler(failures::add);
        List<String> modes = args.length > 0 ? List.of(args) : List.of(
                "after", "afterAll", "hopAfter", "twoCondAfter", "subsetAfter", "queuedCheck", "external",
                "inside", "thenRun", "hopInside", "subsetInside");
        var bad = new ArrayList<String>();
        for (var mode : modes) {
            int lost = 0;
            for (int k = 0; k < TRIALS; k++) {
                if (!trial(mode)) {
                    lost++;
                }
            }
            System.out.println("TestCondLostWakeup: " + mode + ": lost " + lost + " of " + TRIALS);
            if (lost != 0) {
                bad.add(mode + " (" + lost + " of " + TRIALS + ")");
            }
        }
        boundary();
        System.out.println("TestCondLostWakeup: boundary: as documented");
        check(failures.isEmpty(), "no task failed: " + failures);
        if (!bad.isEmpty()) {
            throw new AssertionError("FAILED: lost wakeups in " + bad);
        }
        System.out.println("TestCondLostWakeup: all modes woke in all trials");
    }
}
