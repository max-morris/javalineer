package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.FreshStackWaitProbe;
import edu.lsu.cct.javalineer.Guard;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Guard.callOnFreshStack when the JVM itself raises a StackOverflowError in the caller's wait (Guardian's hole 71),
 * after the call has been handed to a helper. TestFreshStackWait throws from the test hook, a Java throw from a call
 * that has already returned; this test has the real thing, at the real edge of the stack, where a continued wait that
 * made a call would overflow again (after the review of the fix, finding 1, whose driver this follows).
 * <p>
 * A thread with a 256 KB stack recurses until it overflows, then, at each of the 60 deepest levels while unwinding,
 * makes a call on a fresh stack that runs 15 ms. The test hook, at one point of the wait, recurses a few frames, more
 * than the steps before the handoff need, so that at the levels closest to the edge the handoff succeeds and the wait
 * overflows. For every throwable the caller gets, the call's state then must be "finished" (thrown after the
 * handoff) or "not started" (thrown before it, and then the call must never start); the trampoline must be back.
 * Each case must see at least one throwable after the handoff, so that it can't pass empty. Cases: the hook before
 * the park with the caller itself a helper (a nested call), before the park, and after the unpark. So that the nested
 * case's dive to the edge of a helper's stack is short, the test runs itself in a child JVM with
 * JAVALINEER_FRESH_STACK_SIZE=256k unless that is already set, passing on its own JVM options, and with the dive and
 * the hook's recursion kept from being inlined into themselves.
 */
public class TestFreshStackEdge {
    static final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger checks = new AtomicInteger();
    static final long WATCHDOG_SECONDS = 200;
    static final int BODY_MS = 15, LEVELS = 60, MIN_ROUNDS = 4, MAX_ROUNDS = 16;
    /**
     * The hook's depths, in frames, one per round in turn. Which depth lands after the handoff depends on the JIT's
     * state (frame sizes), so a case that finds nothing at one goes on to the next.
     */
    static final int[] BURNS = {16, 8, 24, 40, 6, 12, 32, 10};

    static void check(boolean b, String what) {
        checks.incrementAndGet();
        if (!b) {
            failures.add(what);
        }
    }

    static volatile long sink;
    static volatile int burnDepth;
    static volatile String point = "";

    // What the attempts of the current case found.
    static int ok, afterHandoff, beforeHandoff, leftEarly, lentAfter;
    static Throwable firstBefore;
    static final List<AtomicInteger> notStarted = Collections.synchronizedList(new ArrayList<>());

    static long burn(int n, long a, long b, long c, long d) {
        if (n == 0) {
            return a;
        }
        return burn(n - 1, a + 1, b ^ a, c + b, d ^ c) + b;
    }

    static void attempt() {
        AtomicInteger st = new AtomicInteger(); // 0 not started, 1 running, 2 finished
        int lentBefore = FreshStackWaitProbe.lent();
        try {
            Guard.callOnFreshStack(() -> {
                st.set(1);
                long end = System.nanoTime() + BODY_MS * 1_000_000L;
                while (System.nanoTime() < end) {
                    Thread.onSpinWait();
                }
                st.set(2);
                return null;
            });
            ok++;
        } catch (Throwable e) {
            int s = st.get();
            if (s == 2) {
                afterHandoff++;
            } else if (s == 1) {
                leftEarly++;
            } else {
                beforeHandoff++;
                notStarted.add(st);
                if (firstBefore == null) {
                    firstBefore = e;
                }
            }
            if (FreshStackWaitProbe.lent() != lentBefore) {
                lentAfter++;
            }
        }
    }

    static int dive(int d, long a, long b, long c) {
        int r;
        try {
            r = dive(d + 1, a + 1, b ^ d, c + a);
        } catch (StackOverflowError e) {
            r = 0;
        }
        if (r < LEVELS) {
            attempt();
        }
        return r + 1;
    }

    static void runCase(String at, boolean nested) throws InterruptedException {
        ok = afterHandoff = beforeHandoff = leftEarly = lentAfter = 0;
        firstBefore = null;
        point = at;
        int rounds = 0;
        while (rounds < MIN_ROUNDS || (afterHandoff == 0 && rounds < MAX_ROUNDS)) {
            burnDepth = BURNS[rounds % BURNS.length];
            Thread t = new Thread(null, () -> {
                if (nested) {
                    Guard.callOnFreshStack(() -> dive(0, 0, 0, 0));
                } else {
                    dive(0, 0, 0, 0);
                }
            }, "edge", 256 << 10);
            t.start();
            t.join();
            rounds++;
        }
        burnDepth = 0;
        String name = at + (nested ? ", nested" : "");
        if (Boolean.getBoolean("edgeDebug") && firstBefore != null) {
            StackTraceElement[] tr = firstBefore.getStackTrace();
            for (int i = 0; i < Math.min(14, tr.length); i++) {
                System.out.println("    at " + tr[i]);
            }
        }
        System.out.println(name + ": " + rounds + " rounds; " + ok + " calls returned, " + afterHandoff
                           + " overflows after the handoff, " + beforeHandoff + " before it, " + leftEarly
                           + " callers back while the call ran, " + lentAfter + " with the trampoline still lent"
                           + (firstBefore == null ? "" : "; first throwable before the handoff: " + firstBefore));
        check(afterHandoff > 0, name + ": no overflow landed after the handoff, so the case tested nothing");
        check(leftEarly == 0, name + ": " + leftEarly + " callers came back while their call still ran");
        check(lentAfter == 0, name + ": " + lentAfter + " callers came back with the trampoline still lent");
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        if (System.getProperty("JAVALINEER_FRESH_STACK_SIZE") == null) {
            List<String> cmd = new ArrayList<>();
            cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            cmd.add("-DJAVALINEER_FRESH_STACK_SIZE=256k");
            // One frame per level of the dive and of the hook's burn, so that both span the same stack whatever the
            // JIT inlines: with either inlined into itself, every attempt can fall short of the handoff, or the
            // hook's burn short of the edge.
            cmd.add("-XX:CompileCommand=quiet");
            cmd.add("-XX:CompileCommand=dontinline," + TestFreshStackEdge.class.getName() + "::dive");
            cmd.add("-XX:CompileCommand=dontinline," + TestFreshStackEdge.class.getName() + "::burn");
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add(TestFreshStackEdge.class.getName());
            Process child = new ProcessBuilder(cmd).inheritIO().start();
            System.exit(child.waitFor());
        }
        Thread w = new Thread(() -> {
            sleep(WATCHDOG_SECONDS * 1000);
            System.out.println("FAILED: no end after " + WATCHDOG_SECONDS + " s; failures so far: " + failures);
            System.out.flush();
            Runtime.getRuntime().halt(1);
        }, "watchdog");
        w.setDaemon(true);
        w.start();

        // Load and link everything the edge will run, with the hook in place but burning nothing, so that no class
        // is first loaded at the edge of the stack.
        FreshStackWaitProbe.setInjector(p -> {
            if (p.equals(point)) {
                sink = burn(burnDepth, 1, 2, 3, 4);
            }
        });
        point = "BEFORE_PARK";
        burnDepth = 0;
        for (int i = 0; i < 200; i++) {
            attempt();
        }
        check(ok == 200, "the warm-up calls returned (" + ok + " of 200)");

        // The nested case first: a nested call at the edge needs a second helper, idle, since starting a thread there
        // would overflow before the handoff every time, so make one now (an idle helper lasts 10 s). It also runs
        // first because once the plain cases have compiled the dive, on this JVM, every nested attempt overflowed
        // before the handoff, which tests nothing.
        burnDepth = 0;
        for (int i = 0; i < 20; i++) {
            Guard.callOnFreshStack(() -> Guard.callOnFreshStack(() -> 1));
        }
        runCase("BEFORE_PARK", true);
        runCase("BEFORE_PARK", false);
        runCase("AFTER_UNPARK", false);
        FreshStackWaitProbe.setInjector(null);

        // A call not started when its caller got the throwable must never start: wake every helper, as its idle
        // timeout would, and look again.
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("javalineer-fresh-stack-")) {
                LockSupport.unpark(t);
            }
        }
        sleep(1_000);
        int late = 0;
        for (AtomicInteger st : notStarted) {
            if (st.get() != 0) {
                late++;
            }
        }
        check(late == 0, late + " calls not started when their caller got its throwable started later");
        check(FreshStackWaitProbe.lent() == 0, "the main thread's trampoline is not lent");

        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("TestFreshStackEdge passed (" + checks.get() + " checks)");
        System.exit(0);
    }
}
