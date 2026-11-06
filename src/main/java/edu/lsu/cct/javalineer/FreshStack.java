package edu.lsu.cct.javalineer;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * The mechanism behind {@link Guard#callOnFreshStack(Supplier, Runnable, Runnable)}: runs a call on another thread's
 * stack, a helper's, while the caller waits for it, with the caller's guard state lent to the helper for the call.
 * <p>
 * Helpers are platform threads with a big stack ({@link #SIZE}), kept and reused: a helper that has finished a call
 * goes on an idle stack (LIFO, so the most recently used, whose stack and caches are warm, is taken first) and waits
 * there for the next, for {@link #KEEP_ALIVE_NANOS}, after which it ends, so that the stack pages a deep call touched
 * are given back. Why not a thread per call: a platform thread costs about 200 us to start and join, against about 3
 * us to hand a call to a parked helper (proof/notes/javalineer-fresh-stack.md in Guardian has the numbers). Why not a
 * virtual thread: its stack is no bigger than a platform thread's default, so it gives about 450 times fewer frames
 * per handoff than a 256 MB helper, costs about 10 us a handoff, and needs Java 21, where this library builds for 11.
 * <p>
 * The handoff is ordered as a thread's start and join would order it: the caller writes everything the call may read
 * (including the guard state lent) before its volatile write of the helper's {@code job}, which the helper reads before
 * running the call; the helper writes everything the call did (its result, and the lent state as the call left it)
 * before its volatile write of the call's {@code done}, which the caller reads before going on. So the caller and the
 * helper never touch the lent state at the same time, and each sees all the other's writes to it.
 */
final class FreshStack {
    private FreshStack() { }

    /**
     * The stack size requested for each helper, in bytes: JAVALINEER_FRESH_STACK_SIZE (a number of bytes, optionally
     * followed by k, m or g), 256 MB by default. Only reserved address space until touched: on Linux a thread's stack
     * is committed page by page as it grows. A JVM may round or (on some platforms) ignore the request.
     */
    static final long SIZE = parseSize(System.getProperty("JAVALINEER_FRESH_STACK_SIZE"), 256L << 20);

    /** How long an idle helper waits for another call before it ends. */
    private static final long KEEP_ALIVE_NANOS = 10_000_000_000L;

    private static final ConcurrentLinkedDeque<Helper> IDLE = new ConcurrentLinkedDeque<>();

    private static final AtomicLong HELPERS = new AtomicLong();

    static long parseSize(String s, long dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        String t = s.strip().toLowerCase(Locale.ROOT);
        long mult = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k' || last == 'm' || last == 'g') {
            mult = last == 'k' ? 1L << 10 : last == 'm' ? 1L << 20 : 1L << 30;
            t = t.substring(0, t.length() - 1);
        }
        long n;
        try {
            n = Long.parseLong(t) * mult;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JAVALINEER_FRESH_STACK_SIZE: '" + s + "'", e);
        }
        if (n <= 0) {
            throw new IllegalArgumentException("Invalid JAVALINEER_FRESH_STACK_SIZE: '" + s + "'");
        }
        return n;
    }

    /** One call, from the caller to a helper and back. */
    private static final class Call<T> {
        final Supplier<T> body;
        final Runnable enter, exit;
        final GuardTask.Trampoline lent;
        final ClassLoader loader;
        final boolean interruptedBefore;
        final Thread waiter;
        T result;
        Throwable failure;
        boolean interruptedAfter;
        volatile boolean done;

        Call(Supplier<T> body, Runnable enter, Runnable exit, GuardTask.Trampoline lent, ClassLoader loader,
             boolean interruptedBefore, Thread waiter) {
            this.body = body;
            this.enter = enter;
            this.exit = exit;
            this.lent = lent;
            this.loader = loader;
            this.interruptedBefore = interruptedBefore;
            this.waiter = waiter;
        }

        /**
         * On the helper: installs the caller's guard state (and its context class loader and interrupt status), runs
         * enter, the body and exit, and uninstalls them again, catching everything. Javalineer's state is installed
         * first and removed last, so the hooks see the guards held as the body does.
         */
        void runHere() {
            Thread me = Thread.currentThread();
            me.setContextClassLoader(loader);
            if (interruptedBefore) {
                me.interrupt();
            }
            GuardTask.Trampoline.lend(lent);
            // Strict nesting: every body the call starts inline ends before it returns, so the held guards, the
            // count of bodies and the loop's running successor are as the caller left them. Only the slot may change
            // (a release during the call offers its successor there, for the caller's loop to run next).
            final int bodies = lent.bodies;
            final GuardSet held = lent.held;
            final GuardTask running = lent.running;
            try {
                boolean entered = false;
                try {
                    if (enter != null) {
                        enter.run();
                    }
                    entered = true;
                    result = body.get();
                } catch (Throwable t) {
                    failure = t;
                } finally {
                    if (entered && exit != null) {
                        try {
                            exit.run();
                        } catch (Throwable t) {
                            if (failure == null) {
                                failure = t;
                            } else if (failure != t) {
                                failure.addSuppressed(t);
                            }
                        }
                    }
                }
            } finally {
                assert lent.bodies == bodies && lent.held == held && lent.running == running
                        : "a call on a fresh stack left Javalineer's guard state changed";
                GuardTask.Trampoline.giveBack(lent);
                interruptedAfter = Thread.interrupted();
                me.setContextClassLoader(null);
            }
        }

        /** On the helper, after {@link #runHere()}: publishes the outcome and wakes the caller. */
        void finish() {
            done = true;
            LockSupport.unpark(waiter);
        }
    }

    private static final class Helper implements Runnable {
        /** The call handed to this helper and not yet taken by it, or null. Written by the caller, cleared here. */
        volatile Call<?> job;
        final Thread thread;

        Helper() {
            thread = new Thread(null, this, "javalineer-fresh-stack-" + HELPERS.incrementAndGet(), SIZE);
            thread.setDaemon(true);
        }

        @Override
        public void run() {
            long idleSince = System.nanoTime();
            while (true) {
                Call<?> c = job;
                if (c == null) {
                    LockSupport.parkNanos(this, KEEP_ALIVE_NANOS);
                    if (job == null && System.nanoTime() - idleSince >= KEEP_ALIVE_NANOS
                            && IDLE.removeFirstOccurrence(this)) {
                        // Off the idle stack, so no caller can hand this helper a call any more.
                        return;
                    }
                    // A spurious wakeup, or a caller has taken this helper from the idle stack and its call is
                    // on the way (or here): look again.
                    continue;
                }
                job = null;
                try {
                    c.runHere();
                } catch (Throwable t) {
                    // runHere catches everything the call and the hooks throw; only an error in its own bookkeeping
                    // could get here, and the caller must still be told, or it would wait forever.
                    if (c.failure == null) {
                        c.failure = t;
                    }
                }
                // Idle again before the caller is woken, so that a caller making call after call finds this helper.
                // The caller is woken even if that fails (an allocation failure), and this helper then ends.
                try {
                    IDLE.push(this);
                    idleSince = System.nanoTime();
                } finally {
                    c.finish();
                }
            }
        }
    }

    static <T> T call(Supplier<T> body, Runnable enter, Runnable exit) {
        Objects.requireNonNull(body, "body");
        Thread me = Thread.currentThread();
        GuardTask.Trampoline lent = GuardTask.Trampoline.current();
        Helper h = IDLE.pollFirst();
        boolean fresh = h == null;
        if (fresh) {
            try {
                h = new Helper();
            } catch (Throwable t) {
                throw cannotStart(t);
            }
        }
        // The caller's interrupt status goes with the call, as the rest of its thread state does.
        var c = new Call<>(body, enter, exit, lent, me.getContextClassLoader(), Thread.interrupted(), me);
        h.job = c;
        if (fresh) {
            try {
                h.thread.start();
            } catch (Throwable t) {
                if (c.interruptedBefore) {
                    me.interrupt();
                }
                throw cannotStart(t);
            }
        } else {
            LockSupport.unpark(h.thread);
        }
        // Like Thread.join and Pool.join, the wait is not interruptible: an interrupt is remembered and set again.
        boolean interrupted = false;
        while (!c.done) {
            LockSupport.park(c);
            if (Thread.interrupted()) {
                interrupted = true;
            }
        }
        if (interrupted || c.interruptedAfter) {
            me.interrupt();
        }
        if (c.failure != null) {
            throw FreshStack.<RuntimeException>sneaky(c.failure);
        }
        return c.result;
    }

    /**
     * The loud failure: no fresh stack could be started (the JVM or the OS refused a thread), so the call did not run
     * and the caller's state is untouched. An Error, since a caller can't recover from it by retrying on the same
     * stack, which is what the fresh stack was for.
     */
    private static OutOfMemoryError cannotStart(Throwable cause) {
        var e = new OutOfMemoryError("Javalineer: could not start a thread for a fresh stack (" + cause
                                     + "); the call did not run");
        e.initCause(cause);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable t) throws E {
        throw (E) t;
    }
}
