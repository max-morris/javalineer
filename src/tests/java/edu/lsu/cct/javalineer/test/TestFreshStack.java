package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.FreshStackProbe;
import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.GuardVar;
import edu.lsu.cct.javalineer.Pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Guard.callOnFreshStack: a call on another thread's stack, with this thread's guard state lent, is a plain call to
 * everything Javalineer does. Checked: the value and the thread; nested acquisitions inside the call (inline when
 * free, queued behind a guard held below, and the queued one running after the enclosing body in order); Guard.has,
 * Var.get and assertLocked inside it; a successor offered to the trampoline's slot during it (FreshStackProbe);
 * exceptions, errors and a stack overflow coming back as the same object, with failure reporting as for an inline
 * call; the hooks, the interrupt status and the context class loader; Pool.join refusing to wait on a fresh stack;
 * deep recursions through free guards with repeated and nested handoffs, which overflow without them; helpers
 * reused; and a convoy of multi-guard transfers whose every pool task, trampoline loops included, runs on a fresh
 * stack, under whichever pool the run selects.
 */
public class TestFreshStack {
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

    static int overflow(int d) {
        return overflow(d + 1) + 1;
    }

    static int plain(int d) {
        return d == 0 ? 0 : plain(d - 1) + 1;
    }

    /**
     * Level i of a recursion through free guards: the body of gs[i], run inline (the guard is free), makes level
     * i + 1, on a fresh stack when the levels since the last one reach the bound for this stack (first on the
     * caller's own, then on each fresh one). Returns the sum of the values.
     */
    static long deep(GuardVar<Integer>[] gs, int i, int sinceFresh, int firstBound, int freshBound, boolean onFresh,
                     AtomicInteger handoffs, AtomicInteger bad) {
        if (i == gs.length) {
            return 0;
        }
        long[] r = new long[1];
        Guard.runGuarded(gs[i], v -> {
            int x = v.get();
            if (!Guard.has(gs[i]) || (i > 0 && Guard.has(gs[i - 1]))) {
                bad.incrementAndGet();
            }
            int bound = onFresh ? freshBound : firstBound;
            if (sinceFresh + 1 >= bound) {
                handoffs.incrementAndGet();
                r[0] = Guard.callOnFreshStack(() -> deep(gs, i + 1, 0, firstBound, freshBound, true, handoffs, bad));
            } else {
                r[0] = deep(gs, i + 1, sinceFresh + 1, firstBound, freshBound, onFresh, handoffs, bad);
            }
            if (!Guard.has(gs[i]) || v.get() != x) {
                bad.incrementAndGet();
            }
            r[0] += x;
        });
        return r[0];
    }

    @SuppressWarnings("unchecked")
    static void deepCase(String name, int n, int firstBound, int freshBound) throws Exception {
        GuardVar<Integer>[] gs = new GuardVar[n];
        for (int i = 0; i < n; i++) {
            gs[i] = new GuardVar<>(1);
        }
        var handoffs = new AtomicInteger();
        var bad = new AtomicInteger();
        // On a pool thread, so the caller who blocks is a pool worker, as in Guardian.
        long sum = Pool.run(() -> CompletableFuture.completedFuture(
                deep(gs, 0, 0, firstBound, freshBound, false, handoffs, bad))).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(sum == n, name + ": the sum over " + n + " levels is " + sum);
        check(bad.get() == 0, name + ": " + bad.get() + " levels saw the wrong guards held");
        check(handoffs.get() > 0, name + ": no handoff");
        System.out.println(name + ": " + n + " levels, " + handoffs.get() + " handoffs");
    }

    public static void main(String[] args) throws Exception {
        Test.requireAssert();
        var reports = new AtomicInteger();
        Pool.setTaskFailureHandler(t -> reports.incrementAndGet());

        // The value, the thread, the context class loader, and the trampoline handed back.
        Thread caller = Thread.currentThread();
        ClassLoader loader = new ClassLoader(TestFreshStack.class.getClassLoader()) { };
        ClassLoader oldLoader = caller.getContextClassLoader();
        caller.setContextClassLoader(loader);
        Thread[] ran = new Thread[1];
        int v = Guard.callOnFreshStack(() -> {
            ran[0] = Thread.currentThread();
            check(Thread.currentThread().getContextClassLoader() == loader, "the context class loader is lent");
            return 42;
        });
        caller.setContextClassLoader(oldLoader);
        check(v == 42, "the call's value is returned");
        check(ran[0] != caller, "the call ran on another thread");
        check(!Guard.has(new Guard()), "no guard is held outside a body");
        check(FreshStackProbe.notLentAfter(), "the trampoline is the caller's during the call and not lent after");
        // A trivial frame takes under 100 bytes; 200 leaves room. At the default size, a million frames.
        int frames = (int) Math.min(1_000_000, Guard.freshStackSize() / 200);
        check(Guard.callOnFreshStack(() -> plain(frames)) == frames,
              "a fresh stack takes " + frames + " plain frames");

        // Interrupt status, both ways.
        caller.interrupt();
        boolean seen = Guard.callOnFreshStack(() -> Thread.interrupted());
        check(seen && !caller.isInterrupted(), "the caller's interrupt goes to the call, which cleared it");
        Guard.callOnFreshStack(() -> {
            Thread.currentThread().interrupt();
            return null;
        });
        check(Thread.interrupted(), "an interrupt the call leaves set comes back to the caller");

        // Hooks: the caller's own thread-local state, lent by enter and exit.
        ThreadLocal<String> work = new ThreadLocal<>();
        work.set("caller's");
        String mine = work.get();
        String inside = Guard.callOnFreshStack(work::get, () -> work.set(mine), work::remove);
        check("caller's".equals(inside), "enter lends the caller's thread-local (got " + inside + ")");
        check(Guard.callOnFreshStack(work::get) == null, "exit removed it from the helper");
        check("caller's".equals(work.get()), "the caller's thread-local is untouched");
        boolean[] ranCall = new boolean[1], ranExit = new boolean[1];
        var enterFails = new IllegalStateException("enter fails");
        Throwable t1 = null;
        try {
            Guard.callOnFreshStack(() -> ranCall[0] = true, () -> { throw enterFails; }, () -> ranExit[0] = true);
        } catch (Throwable t) {
            t1 = t;
        }
        check(t1 == enterFails && !ranCall[0] && !ranExit[0], "a failing enter runs neither the call nor exit");
        var callFails = new RuntimeException("call fails");
        var exitFails = new RuntimeException("exit fails");
        Throwable t2 = null;
        try {
            Guard.callOnFreshStack(() -> { throw callFails; }, null, () -> { throw exitFails; });
        } catch (Throwable t) {
            t2 = t;
        }
        check(t2 == callFails && callFails.getSuppressed().length == 1 && callFails.getSuppressed()[0] == exitFails,
              "exit's failure is suppressed under the call's");

        // Exceptions come back as the same object, unreported.
        var rte = new RuntimeException("expected by TestFreshStack");
        var err = new AssertionError("expected by TestFreshStack");
        var checked = new Exception("expected by TestFreshStack");
        check(thrownBy(() -> { throw rte; }) == rte, "a RuntimeException comes back as itself");
        check(thrownBy(() -> { throw err; }) == err, "an Error comes back as itself");
        check(thrownBy(() -> { throw sneaky(checked); }) == checked, "a checked exception comes back as itself");
        check(thrownBy(() -> overflow(0)) instanceof StackOverflowError, "a stack overflow comes back");
        check(reports.get() == 0, "nothing thrown by a call was reported (" + reports.get() + ")");

        // Inside a guarded body (a is free, so it runs here): nested acquisitions on the fresh stack.
        var a = new GuardVar<>(1);
        var b = new GuardVar<>(2);
        var c = new GuardVar<>(3);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<?>[] queued = new CompletableFuture<?>[1];
        Guard.runGuarded(a, av -> {
            Guard.callOnFreshStack(() -> {
                order.add("call");
                check(Guard.has(a) && !Guard.has(b), "on the fresh stack a is held, b not");
                check(av.get() == 1, "Var.get of a on the fresh stack");
                check(FreshStackProbe.heldIsLentAndLocked(), "held is lent and assertLocked passes (nested)");
                boolean[] inline = new boolean[1];
                Guard.runGuarded(b, bv -> {
                    inline[0] = true;
                    check(Guard.has(b) && bv.get() == 2, "the nested body of free b holds b");
                });
                check(inline[0], "a nested acquisition of free b ran inline on the fresh stack");
                check(Guard.has(a) && !Guard.has(b), "a is held again after b's body, b not");
                Guard.runGuarded(b, c, (bv, cv) -> {
                    check(Guard.has(b) && Guard.has(c) && bv.get() + cv.get() == 5, "nested b and c inline");
                });
                queued[0] = Guard.runGuarded(a, av2 -> order.add("queued on a"));
                check(!queued[0].isDone(), "an acquisition of a, held below, is queued");
                Guard.runGuarded(b, bv -> {
                    throw new RuntimeException("expected by TestFreshStack: a body failing on a fresh stack");
                });
                order.add("call returns");
                return null;
            });
            order.add("after the call");
            check(Guard.has(a) && av.get() == 1, "the caller's body still holds a");
            check(FreshStackProbe.heldIsLentAndLocked(), "held is lent and assertLocked passes");
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        queued[0].get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(order.equals(List.of("call", "call returns", "after the call", "queued on a")),
              "the queued successor runs after the call and the body, in order: " + order);
        check(reports.get() == 1, "the failing nested body was reported once, as inline (" + reports.get() + ")");

        // A call failing inside a guarded body fails that body's future, reported once, as an inline throw would.
        var bodyFut = Guard.runGuarded(a, av -> Guard.callOnFreshStack(() -> { throw rte; }));
        Throwable bodyFailure = null;
        try {
            bodyFut.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            bodyFailure = e.getCause();
        }
        check(bodyFailure == rte && reports.get() == 2, "a call's throw fails the body's future, reported once");

        // A multi-guard body, and inside a pool task.
        Pool.run(() -> Guard.runGuarded(a, b, (av, bv) -> {
            Guard.callOnFreshStack(() -> {
                check(Guard.has(a) && Guard.has(b) && !Guard.has(c), "a multi-guard body's guards on a fresh stack");
                check(FreshStackProbe.heldIsLentAndLocked(), "assertLocked on a multi-guard body's set");
                Guard.runGuarded(c, cv -> check(Guard.has(c) && cv.get() == 3, "nested c inline"));
                return null;
            });
        })).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The trampoline's slot.
        String slot = FreshStackProbe.slotAcrossCall();
        check(slot.equals("call,successor"), "a successor offered to the slot during the call runs after it: " + slot);

        // Pool.join on a fresh stack: refuses to wait, returns a value it needn't wait for.
        Throwable joinFailure = thrownBy(() -> Pool.join(new CompletableFuture<Integer>()));
        check(joinFailure instanceof IllegalStateException, "Pool.join refuses to wait on a fresh stack: " + joinFailure);
        check(Guard.callOnFreshStack(() -> Pool.join(CompletableFuture.completedFuture(7))) == 7,
              "Pool.join of a completed future on a fresh stack");

        // Helpers are reused: sequential calls need one or two.
        var helpers = new HashSet<Thread>();
        for (int i = 0; i < 2_000; i++) {
            helpers.add(Guard.callOnFreshStack(Thread::currentThread));
        }
        check(helpers.size() <= 4, "2,000 sequential calls used " + helpers.size() + " helpers");

        // Deep recursions through free guards. Without the handoffs these overflow a 1 MB stack after a few
        // thousand levels.
        deepCase("deep, a handoff every 200 levels", 20_000, 200, 200);
        // On a fresh stack, a bound scaled by its size (4 KB a level: a level is a guarded body's few frames).
        int freshBound = (int) Math.min(50_000, Guard.freshStackSize() / 4096);
        deepCase("deep, every 200 levels on the caller's stack, " + freshBound + " on a fresh one", 200_000, 200,
                 freshBound);

        // Concurrent: transfers between accounts under two guards, each body making a call on a fresh stack which
        // nests an acquisition of a free audit guard, with the same done again from inside that call.
        int accounts = 16, transfers = 20_000;
        @SuppressWarnings("unchecked")
        GuardVar<Integer>[] acct = new GuardVar[accounts];
        AtomicInteger[] busy = new AtomicInteger[accounts];
        for (int i = 0; i < accounts; i++) {
            acct[i] = new GuardVar<>(1_000);
            busy[i] = new AtomicInteger();
        }
        var exclusion = new AtomicInteger();
        Runnable bank = () -> {
            var rnd = new Random(1);
            var futs = new ArrayList<CompletableFuture<?>>();
            for (int k = 0; k < transfers; k++) {
                int x = rnd.nextInt(accounts), y = (x + 1 + rnd.nextInt(accounts - 1)) % accounts;
                int amount = rnd.nextInt(10);
                boolean nest = k % 3 == 0;
                futs.add(Pool.run(() -> Guard.runGuarded(acct[x], acct[y], (xv, yv) -> {
                    if (busy[x].incrementAndGet() != 1 | busy[y].incrementAndGet() != 1) {
                        exclusion.incrementAndGet();
                    }
                    var audit = new GuardVar<>(0);
                    Guard.callOnFreshStack(() -> {
                        Supplier<Void> move = () -> {
                            Guard.runGuarded(audit, av -> av.set(av.get() + 1));
                            xv.set(xv.get() - amount);
                            yv.set(yv.get() + amount);
                            return null;
                        };
                        return nest ? Guard.callOnFreshStack(move) : move.get();
                    });
                    busy[x].decrementAndGet();
                    busy[y].decrementAndGet();
                })));
            }
            for (var f : futs) {
                try {
                    f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failures.add("a transfer failed: " + e);
                }
            }
        };
        bank.run();

        // The same again with every pool task, trampoline loops and handoffs included, run on a fresh stack: the
        // pool selected by the run is wrapped so that each task is a call on a fresh stack from its worker.
        Executor selected = Pool.getPool();
        Pool.setPool(task -> selected.execute(() -> Guard.callOnFreshStack(() -> {
            task.run();
            return null;
        })), false);
        bank.run();
        Pool.setPool(selected, false);

        int[] sum = new int[1];
        CompletableFuture<?>[] reads = new CompletableFuture<?>[accounts];
        for (int i = 0; i < accounts; i++) {
            reads[i] = Guard.runGuarded(acct[i], gv -> {
                synchronized (sum) {
                    sum[0] += gv.get();
                }
            });
        }
        CompletableFuture.allOf(reads).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        check(sum[0] == accounts * 1_000, "transfers conserve the total: " + sum[0]);
        check(exclusion.get() == 0, exclusion.get() + " transfers overlapped on an account");
        check(reports.get() == 2, "no further failures were reported (" + reports.get() + ")");

        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("TestFreshStack passed (" + checks.get() + " checks)");
        System.exit(0);
    }
}
