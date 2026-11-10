package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.FreshStackWaitProbe;
import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.Pool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Guard.callOnFreshStack when the caller itself fails during the handoff (Guardian's hole 71): a throw on the caller
 * after the call is handed to a helper (injected through FreshStack's test hook, standing for a StackOverflowError at
 * the edge of a deep caller's stack) must not let the caller leave while the helper still runs the call with the
 * caller's guard state. Checked:
 * <ul>
 * <li>A throw right after a new helper's thread has started (so the start counts as failed though the thread runs):
 *     the call does not run, the caller gets the OutOfMemoryError that says so, and the started thread ends.</li>
 * <li>A throw at each point of the wait (before and after the unpark, before and after the park, after the interrupt
 *     check): the caller comes back only after the call has finished and the trampoline is given back, with the
 *     injected throwable, and the call's own throwable, if any, suppressed under it. An interrupt that arrives while
 *     the caller waits after such a throw is kept.</li>
 * <li>giveBack's assertion fires after the trampoline is given back.</li>
 * <li>Mutual exclusion: bodies on two guards, and on both, each making its work in a call on a fresh stack and letting
 *     an injected throw escape (so the guard is released by the body's failure), while throws are injected at random
 *     points. No call runs outside its body's hold of the guard, no update is lost, and no caller leaves before its
 *     call ends.</li>
 * </ul>
 */
public class TestFreshStackWait {
    static final long TIMEOUT_SECONDS = 120;
    static final AtomicInteger checks = new AtomicInteger();
    static final List<String> failures = Collections.synchronizedList(new ArrayList<>());

    static void check(boolean b, String what) {
        checks.incrementAndGet();
        if (!b) {
            failures.add(what);
        }
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException sneaky(Throwable t) throws E {
        throw (E) t;
    }

    static Throwable thrownBy(Supplier<?> s) {
        try {
            Guard.callOnFreshStack(s);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    static Set<Thread> helpers() {
        Set<Thread> s = new HashSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("javalineer-fresh-stack-")) {
                s.add(t);
            }
        }
        return s;
    }

    /** Injects one throw, of the given throwable, at the first time the point is reached on this thread. */
    static void injectOnce(String point, Throwable t) {
        Thread me = Thread.currentThread();
        AtomicBoolean fired = new AtomicBoolean();
        FreshStackWaitProbe.setInjector(p -> {
            if (p.equals(point) && Thread.currentThread() == me && fired.compareAndSet(false, true)) {
                throw sneaky(t);
            }
        });
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void spinMicros(long us) {
        long end = System.nanoTime() + us * 1000;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    /** The state guarded by one guard, and what the bodies on it found. */
    static final class Shared {
        final Guard guard = new Guard();
        /** The body holding the guard now: set at its start, under the guard. */
        final AtomicInteger current = new AtomicInteger();
        /** Calls on a fresh stack running now for bodies on this guard. */
        final AtomicInteger callsRunning = new AtomicInteger();
        /** Read and written plainly, by calls only: a lost update means two calls overlapped. */
        long count;
        /** The calls that ran on this guard, counted atomically, to compare count with. */
        final AtomicInteger ran = new AtomicInteger();
    }

    static final AtomicInteger bodyIds = new AtomicInteger();
    static final AtomicInteger overlaps = new AtomicInteger();
    static final AtomicInteger leftEarly = new AtomicInteger();
    static final AtomicInteger callsRan = new AtomicInteger();
    static final AtomicInteger notRun = new AtomicInteger();
    static final AtomicInteger escaped = new AtomicInteger();
    static final AtomicInteger injected = new AtomicInteger();

    /**
     * A body on the guards of ss: marks itself current on each, makes its work in a call on a fresh stack, and lets
     * whatever that call throws escape (so its guards are released by the body's failure, the route of hole 71).
     */
    static Runnable body(Shared... ss) {
        return () -> {
            int me = bodyIds.incrementAndGet();
            for (Shared s : ss) {
                if (s.callsRunning.get() != 0) {
                    overlaps.incrementAndGet(); // A call of an earlier body still runs as this body starts.
                }
                s.current.set(me);
            }
            AtomicInteger st = new AtomicInteger(); // 0 not started, 1 running, 2 finished
            Throwable thrown = null;
            try {
                Guard.callOnFreshStack(() -> {
                    st.set(1);
                    for (Shared s : ss) {
                        s.callsRunning.incrementAndGet();
                        if (s.current.get() != me) {
                            overlaps.incrementAndGet();
                        }
                    }
                    for (Shared s : ss) {
                        long x = s.count;
                        spinMicros(5);
                        s.count = x + 1;
                        s.ran.incrementAndGet();
                    }
                    for (Shared s : ss) {
                        if (s.current.get() != me) {
                            overlaps.incrementAndGet();
                        }
                        s.callsRunning.decrementAndGet();
                    }
                    callsRan.incrementAndGet();
                    st.set(2);
                    return null;
                });
            } catch (Throwable t) {
                thrown = t;
            }
            boolean didNotRun = thrown instanceof OutOfMemoryError && String.valueOf(thrown.getMessage())
                    .contains("the call did not run");
            if (didNotRun) {
                notRun.incrementAndGet();
                if (st.get() != 0) {
                    leftEarly.incrementAndGet();
                }
            } else if (st.get() != 2) {
                leftEarly.incrementAndGet();
            }
            if (thrown != null) {
                escaped.incrementAndGet();
                throw sneaky(thrown);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var reports = new AtomicInteger();
        Pool.setTaskFailureHandler(t -> reports.incrementAndGet());
        Thread caller = Thread.currentThread();

        // 1. A throw right after a new helper's start returned (first, so that no idle helper exists yet): the start
        // counts as failed, the call is never handed over, and the thread that did start ends.
        Set<Thread> before = helpers();
        var afterStart = new IllegalStateException("injected right after the start");
        injectOnce("AFTER_START", afterStart);
        var ran = new AtomicBoolean();
        Throwable t1 = thrownBy(() -> {
            ran.set(true);
            return null;
        });
        FreshStackWaitProbe.setInjector(null);
        check(t1 instanceof OutOfMemoryError && t1.getCause() == afterStart
              && String.valueOf(t1.getMessage()).contains("the call did not run"),
              "a failed start reports that the call did not run: " + t1);
        Set<Thread> started = helpers();
        started.removeAll(before);
        boolean ended = true;
        for (Thread h : started) {
            h.join(5_000);
            ended &= !h.isAlive();
        }
        check(ended, "the thread started for an abandoned helper ended (" + started + ")");
        check(!ran.get(), "the call of a failed start never ran");
        check(FreshStackWaitProbe.lent() == 0, "a failed start leaves the trampoline unlent");
        check(Guard.callOnFreshStack(() -> 7) == 7, "a call after a failed start works");

        // 2. A throw at each point of the wait. The call sleeps, so that the caller, if it left early, would be seen
        // leaving before the call finished. BEFORE_UNPARK last: the helper is never woken then and finds the call
        // when its idle park times out (up to 10 s).
        for (String point : List.of("AFTER_UNPARK", "BEFORE_PARK", "AFTER_PARK", "AFTER_INTERRUPTED",
                                    "BEFORE_UNPARK")) {
            var inj = new StackOverflowError("injected at " + point);
            injectOnce(point, inj);
            var st = new AtomicInteger();
            long t0 = System.nanoTime();
            Throwable got = thrownBy(() -> {
                st.set(1);
                sleep(200);
                st.set(2);
                return 5;
            });
            long ms = (System.nanoTime() - t0) / 1_000_000;
            FreshStackWaitProbe.setInjector(null);
            check(got == inj, point + ": the caller's own throwable comes back: " + got);
            check(st.get() == 2, point + ": the caller came back only after the call finished (state " + st.get()
                                 + ", after " + ms + " ms)");
            check(FreshStackWaitProbe.lent() == 0, point + ": the trampoline was given back first");
            System.out.println(point + ": caller back after " + ms + " ms");
        }

        // The call's own throwable goes under the caller's as suppressed.
        var callFails = new RuntimeException("the call fails");
        var inj2 = new StackOverflowError("injected at AFTER_UNPARK, with a failing call");
        injectOnce("AFTER_UNPARK", inj2);
        Throwable t2 = thrownBy(() -> {
            sleep(50);
            throw callFails;
        });
        FreshStackWaitProbe.setInjector(null);
        check(t2 == inj2 && Arrays.asList(inj2.getSuppressed()).contains(callFails),
              "the call's throwable is suppressed under the caller's: " + t2 + " "
              + Arrays.toString(inj2.getSuppressed()));

        // An interrupt arriving while the caller waits after a throw is kept.
        var inj3 = new StackOverflowError("injected at BEFORE_PARK, then interrupted");
        injectOnce("BEFORE_PARK", inj3);
        Thread interrupter = new Thread(() -> {
            sleep(100);
            caller.interrupt();
        });
        interrupter.start();
        Throwable t3 = thrownBy(() -> {
            sleep(300);
            return null;
        });
        FreshStackWaitProbe.setInjector(null);
        interrupter.join();
        check(t3 == inj3, "the throwable at BEFORE_PARK comes back: " + t3);
        check(Thread.interrupted(), "an interrupt during the wait after a throw is kept");

        // 3. giveBack's assertion fires after the cleanup.
        String gb = FreshStackWaitProbe.giveBackCleansUpWhenItsAssertionFires();
        check(gb == null, "giveBack: " + gb);

        // 4. Mutual exclusion under injected failures. Throws at random points (not BEFORE_UNPARK, whose 10 s wake-up
        // would make this slow; part 2 covers it), on every thread, at each point one time in 20.
        String[] wait = {"AFTER_START", "AFTER_UNPARK", "BEFORE_PARK", "AFTER_PARK", "AFTER_INTERRUPTED"};
        Set<String> chosen = new HashSet<>(Arrays.asList(wait));
        FreshStackWaitProbe.setInjector(p -> {
            if (chosen.contains(p) && ThreadLocalRandom.current().nextInt(20) == 0) {
                injected.incrementAndGet();
                throw new StackOverflowError("injected at " + p);
            }
        });
        Shared a = new Shared(), b = new Shared();
        int perGuard = 2_000, both = 500;
        List<CompletableFuture<?>> futs = new ArrayList<>();
        for (int i = 0; i < perGuard; i++) {
            futs.add(Guard.runGuarded(a.guard, body(a)));
            futs.add(Guard.runGuarded(b.guard, body(b)));
            if (i % 4 == 0) {
                futs.add(Guard.runGuarded(body(a, b), a.guard, b.guard));
            }
        }
        int total = futs.size();
        CompletableFuture.allOf(futs.stream().map(f -> f.handle((v, t) -> null)).toArray(CompletableFuture[]::new))
                         .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        FreshStackWaitProbe.setInjector(null);
        int failed = 0;
        for (var f : futs) {
            if (f.isCompletedExceptionally()) {
                failed++;
            }
        }
        System.out.println("mutual exclusion: " + total + " bodies, " + injected.get() + " throws injected, "
                           + escaped.get() + " bodies failed, " + notRun.get() + " calls not run");
        check(bodyIds.get() == total, "every body ran (" + bodyIds.get() + " of " + total + ")");
        check(overlaps.get() == 0, overlaps.get() + " overlaps of a call with another body on its guard");
        check(leftEarly.get() == 0, leftEarly.get() + " callers left before their call finished");
        check(callsRan.get() == total - notRun.get(), "every handed-over call ran once (" + callsRan.get() + ")");
        check(a.count == a.ran.get() && b.count == b.ran.get(),
              "no update was lost: " + a.count + " of " + a.ran.get() + ", " + b.count + " of " + b.ran.get());
        check(escaped.get() == injected.get(), "every injected throw failed its body (" + escaped.get() + " of "
                                               + injected.get() + ")");
        check(failed == escaped.get(), "the failed bodies' futures failed (" + failed + ")");
        check(injected.get() > 50, "enough throws were injected to mean something (" + injected.get() + ")");
        check(FreshStackWaitProbe.lent() == 0, "the main thread's trampoline is not lent");

        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("TestFreshStackWait passed (" + checks.get() + " checks)");
        System.exit(0);
    }
}
