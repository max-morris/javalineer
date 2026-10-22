package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Pool {
    private static Executor thePool = new CountingExecutor(initializePool());

    /**
     * Retrieves the pool being used by Javalineer. This is a wrapper around the executor given to
     * {@link #setPool(Executor)} (or chosen by JAVALINEER_POOL) which counts the tasks queued or running, so that
     * {@link #join(CompletableFuture)} can tell when the pool has gone idle. Tasks given to it directly, for instance
     * through {@code CompletableFuture.thenRunAsync(task, Pool.getPool())}, are counted too.
     *
     * @return The pool.
     */
    public static Executor getPool() {
        return thePool;
    }

    /**
     * Sets the pool used by Javalineer. This method is only intended for use when nothing is running, and
     * therefore makes no attempt to be thread-safe. If the previous pool is an ExecutorService, shutdown()
     * is called on it.
     * @param newPool The new pool.
     */
    public static void setPool(Executor newPool) {
        setPool(newPool, true);
    }

    /**
     * Sets the pool used by Javalineer. This method is only intended for use when nothing is running, and
     * therefore makes no attempt to be thread-safe.
     * @param newPool The new pool.
     * @param shutdownOld If true, will call shutdown() on the old pool if it is an ExecutorService.
     */
    public static void setPool(Executor newPool, boolean shutdownOld) {
        var old = ((CountingExecutor) thePool).delegate;
        if (shutdownOld && old instanceof ExecutorService) {
            ((ExecutorService) old).shutdown();
        }
        thePool = newPool instanceof CountingExecutor ? newPool : new CountingExecutor(newPool);
    }

    private static volatile Consumer<? super Throwable> taskFailureHandler = null;

    /**
     * Sets a handler which is notified whenever a task run by Javalineer throws. This includes tasks given to
     * {@code runGuarded}, {@code runCondition}, {@code Pool.run}, {@code Loop}, {@link ReadWriteGuard}, and the
     * partitioned list operations. The handler is purely informational: the failure is still captured into the future
     * associated with the task (if there is one), and the handler is not needed for correctness. By default, no
     * handler is installed and failures are reported only through those futures. The handler is called only for
     * exceptions thrown by a task (or a task returning null where a future is required), not when a future returned
     * by a task completes exceptionally.
     * <p>
     * The handler is called synchronously on the thread that ran the failing task, before the task's future is
     * completed exceptionally. When the task was guarded, its guards may still be held while the handler runs, so the
     * handler should be quick and must not wait on work which needs those guards. Any exception thrown by the handler
     * itself is caught and ignored.
     *
     * @param handler The handler, or null to remove the current handler.
     */
    public static void setTaskFailureHandler(Consumer<? super Throwable> handler) {
        taskFailureHandler = handler;
    }

    /**
     * Retrieves the handler set by {@link #setTaskFailureHandler(Consumer)}.
     *
     * @return The current handler, or null if none is installed.
     */
    public static Consumer<? super Throwable> getTaskFailureHandler() {
        return taskFailureHandler;
    }

    /**
     * Notifies the task failure handler, if one is installed, that a task threw. Never throws.
     */
    static void reportTaskFailure(Throwable t) {
        var handler = taskFailureHandler;
        if (handler != null) {
            try {
                handler.accept(t);
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Hands an exception thrown by a raw task (one given directly to an Executor, rather than through Javalineer's
     * future-returning APIs) to the current thread's uncaught exception handler, as a thread pool would. Used by the
     * pools which run tasks in loops that must survive such exceptions. Never throws.
     */
    static void handleUncaught(Throwable t) {
        try {
            var thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, t);
        } catch (Throwable ignored) { }
    }

    /**
     * Runs r, completing done normally if r returns and exceptionally if r throws. Never throws.
     */
    static void runCapturing(Runnable r, CompletableFuture<Void> done) {
        try {
            r.run();
        } catch (Throwable t) {
            reportTaskFailure(t);
            done.completeExceptionally(t);
            return;
        }
        done.complete(null);
    }

    /**
     * Calls task, returning the future it produces. If task throws or returns null, a failed future is returned
     * instead. Never throws.
     */
    static <T> CompletableFuture<T> callCapturing(Supplier<CompletableFuture<T>> task) {
        CompletableFuture<T> fut;
        try {
            fut = task.get();
            if (fut == null) {
                throw new NullPointerException("Task returned a null future");
            }
        } catch (Throwable t) {
            reportTaskFailure(t);
            return CompletableFuture.failedFuture(t);
        }
        return fut;
    }

    public static void execute(Runnable task) {
        thePool.execute(task);
    }

    /**
     * Runs a task on the pool.
     *
     * @param task The task.
     * @return A future which completes when the task returns, or completes exceptionally if the task throws.
     */
    public static CompletableFuture<Void> run(Runnable task) {
        var done = new CompletableFuture<Void>();

        thePool.execute(() -> runCapturing(task, done));

        return done;
    }

    /**
     * Runs a task which produces a future on the pool.
     *
     * @param task The task.
     * @return A future which completes with the result of the future produced by the task. It completes
     *         exceptionally if the task throws, returns null, or produces a future which completes exceptionally.
     */
    public static <T> CompletableFuture<T> run(Supplier<CompletableFuture<T>> task) {
        var done = new CompletableFuture<T>();

        thePool.execute(() -> {
            callCapturing(task).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    done.completeExceptionally(throwable);
                } else {
                    done.complete(result);
                }
            });
        });

        return done;
    }

    // ---- Task accounting and the deadlock backstop ----

    /*
     * Task accounting: submitted counts the tasks handed to the executor, finished those which have run (or which the
     * executor rejected). A task which submits another does so before it finishes, so submitted == finished only when
     * no task is queued or running. They are global rather than per executor, so tasks still in flight on a pool
     * replaced by setPool keep counting. LongAdder rather than AtomicLong because every task updates both from
     * whichever thread it runs on, and a single shared counter is a contended cache line; LongAdder's striped cells
     * make the update nearly free, and only isIdle, which runs rarely, pays for summing.
     */
    private static final LongAdder submitted = new LongAdder();
    private static final LongAdder finished = new LongAdder();

    /**
     * Whether the pool was idle at some instant during this call. LongAdder.sum() is not atomic, but both counters only
     * grow and a task's finish is always counted after its submission, so reading finished first and submitted second
     * is sound: if t is the moment the first read ends, finishedRead <= finished(t) <= submitted(t) <= submittedRead,
     * so equality means submitted(t) == finished(t), that is, nothing was queued or running at t. Nothing can submit a
     * task after such a moment except a thread outside the pool, so (under join's precondition) the pool stays idle.
     */
    private static boolean isIdle() {
        long f = finished.sum();
        long s = submitted.sum();
        assert f <= s;
        return f == s;
    }

    /** The monitor on which {@link #join} waits; notified when the awaited future completes. */
    private static final Object idleMonitor = new Object();

    /** The longest join waits between checks for an idle pool, in milliseconds. */
    private static final long MAX_IDLE_POLL_MILLIS = 100;

    private static final boolean deadlockCheck =
            !"false".equalsIgnoreCase(System.getProperty("JAVALINEER_DEADLOCK_CHECK", "true").strip());

    /** Counts the tasks given to the executor it wraps; see {@link #isIdle}. */
    private static final class CountingExecutor implements Executor {
        final Executor delegate;

        CountingExecutor(Executor delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public void execute(Runnable task) {
            Objects.requireNonNull(task);
            submitted.increment();
            try {
                delegate.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        finished.increment();
                    }
                });
            } catch (RejectedExecutionException e) {
                // Not accepted, so it will never run. Any other exception leaves the task counted as in flight: that
                // can only keep join from reporting a deadlock, never make it report one falsely.
                finished.increment();
                throw e;
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * Waits for fut to complete, like {@code fut.join()}, but reports a deadlock instead of waiting forever. If every
     * task given to the pool has finished (the pool is idle) while fut is still incomplete, then, provided nothing
     * outside the pool can complete fut, nothing ever will: no task is left to run, and the calling thread is
     * waiting. This writes a report of the pending state to stderr and throws {@link DeadlockException}. The usual
     * cause is a completion cycle (a future completed only by a continuation which waits for that same future,
     * directly or through other futures), a latch signaled fewer times than its count, or a condition never
     * signaled.
     * <p>
     * Precondition: every completion of every future fut depends on is done by Javalineer tasks or by the calling
     * thread before this call, not by some other thread (an I/O thread, a timer, a non-Javalineer executor). That
     * holds for Guardian programs, whose entry point is meant to wait with this. Called from a task running on the
     * pool, the pool is never idle (the caller is a running task), so this behaves exactly like {@code fut.join()}.
     * <p>
     * When fut completes, the behavior is that of {@code fut.join()}: its value is returned, or a
     * {@link CompletionException} is thrown if it failed. Like {@code join()}, the wait is not interruptible; an
     * interrupt is remembered and the thread's interrupt status is set again on return. Setting the system property
     * JAVALINEER_DEADLOCK_CHECK=false makes this exactly {@code fut.join()}.
     *
     * @param fut The future to wait for.
     * @return The value of fut.
     * @throws DeadlockException if the pool is idle while fut is incomplete.
     */
    public static <T> T join(CompletableFuture<T> fut) {
        if (deadlockCheck && !fut.isDone()) {
            awaitDoneOrIdle(fut);
            if (!fut.isDone()) {
                var report = deadlockReport(fut);
                System.err.print(report);
                System.err.flush();
                throw new DeadlockException(report);
            }
        }
        return fut.join();
    }

    private static void awaitDoneOrIdle(CompletableFuture<?> fut) {
        fut.whenComplete((v, t) -> {
            synchronized (idleMonitor) {
                idleMonitor.notifyAll();
            }
        });
        // Completion wakes this at once. Idleness has no wake-up (that would cost every task a check), so it is
        // polled, with a backoff from 1 ms to MAX_IDLE_POLL_MILLIS: a deadlock is reported within about that long.
        boolean interrupted = false;
        long pollMillis = 1;
        synchronized (idleMonitor) {
            while (!fut.isDone() && !isIdle()) {
                try {
                    idleMonitor.wait(pollMillis);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                pollMillis = Math.min(2 * pollMillis, MAX_IDLE_POLL_MILLIS);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- The registry of pending futures (debug level, off by default) ----

    private static volatile boolean trackPending =
            "true".equalsIgnoreCase(System.getProperty("JAVALINEER_TRACK_PENDING", "false").strip());

    private static final AtomicLong nextPendingId = new AtomicLong();

    private static final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    private static final class Pending {
        final long id;
        final CompletableFuture<?> fut;
        final String kind;
        final Object owner;
        final String thread = Thread.currentThread().getName();
        final StackTraceElement[] site = new Throwable().getStackTrace();

        Pending(long id, CompletableFuture<?> fut, String kind, Object owner) {
            this.id = id;
            this.fut = fut;
            this.kind = kind;
            this.owner = owner;
        }
    }

    /**
     * Turns the registry of pending futures on or off (see {@link #trackPending(CompletableFuture, String, Object)}).
     * It starts on if the system property JAVALINEER_TRACK_PENDING is "true", and off otherwise. Futures registered
     * while it is off are not listed later.
     *
     * @param on Whether to track.
     */
    public static void setTrackPending(boolean on) {
        trackPending = on;
    }

    /**
     * @return Whether the registry of pending futures is on.
     */
    public static boolean isTrackingPending() {
        return trackPending;
    }

    /**
     * Records that something waits for fut (a continuation, a condition task, a latch), so that a deadlock report
     * from {@link #join} can list it while it is incomplete. The entry is removed when fut completes. This is a
     * debugging aid: when tracking is off (the default) it does nothing beyond one volatile read; when on, each call
     * records the registering thread and stack, so it is too slow to leave on in production.
     *
     * @param fut   The future being waited for.
     * @param kind  What waits for it, for the report (for instance "condition task").
     * @param owner The object it belongs to, for the report, or null.
     * @return fut.
     */
    public static <T> CompletableFuture<T> trackPending(CompletableFuture<T> fut, String kind, Object owner) {
        if (!trackPending || fut.isDone()) {
            return fut;
        }
        long id = nextPendingId.incrementAndGet();
        pending.put(id, new Pending(id, fut, kind, owner));
        fut.whenComplete((v, t) -> pending.remove(id));
        return fut;
    }

    private static final int REPORT_FRAMES = 6;

    private static String deadlockReport(CompletableFuture<?> fut) {
        var sb = new StringBuilder();
        sb.append("Javalineer: deadlock: every task given to the pool has finished, but the awaited future is still ")
          .append("incomplete, so nothing can ever complete it.\n");
        sb.append("  awaited future: ").append(fut).append(" (its dependents include this wait)\n");

        var still = new ArrayList<Pending>();
        for (var p : pending.values()) {
            if (!p.fut.isDone()) {
                still.add(p);
            }
        }
        still.sort(Comparator.comparingLong(p -> p.id));

        if (!trackPending && still.isEmpty()) {
            sb.append("  pending futures: not tracked; rerun with -DJAVALINEER_TRACK_PENDING=true (or call ")
              .append("Pool.setTrackPending(true) first) to list the incomplete condition tasks, latches, and ")
              .append("registered continuations, with where each was registered.\n");
        } else {
            sb.append("  pending futures registered with Pool.trackPending, oldest first: ").append(still.size())
              .append("\n");
            for (var p : still) {
                sb.append("    #").append(p.id).append(' ').append(p.kind);
                if (p.owner != null) {
                    sb.append(" of ").append(p.owner);
                }
                sb.append(": ").append(p.fut).append(p.fut == fut ? " (the awaited future)" : "")
                  .append(", registered on thread ").append(p.thread).append('\n');
                int shown = 0;
                for (var frame : p.site) {
                    if (frame.getClassName().startsWith(Pool.class.getName())) {
                        continue;
                    }
                    sb.append("        at ").append(frame).append('\n');
                    if (++shown == REPORT_FRAMES) {
                        break;
                    }
                }
            }
        }
        sb.append("  likely causes: a completion cycle (a future completed only by a continuation that waits for it, ")
          .append("directly or through other futures), a latch signaled fewer times than its count, or a condition ")
          .append("never signaled.\n");
        return sb.toString();
    }

    private static ThreadFactory getDefaultThreadFactory() {
        return r -> {
            var t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        };
    }

    public static Executor newDefaultPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), getDefaultThreadFactory());
    }

    public static Executor newFixedPool(int nThreads) {
        return Executors.newFixedThreadPool(nThreads, getDefaultThreadFactory());
    }

    public static Executor newDynamicPool() {
        return Executors.newCachedThreadPool(getDefaultThreadFactory());
    }

    public static Executor initializePool() {
        String pool = Optional.ofNullable(System.getProperty("JAVALINEER_POOL", null))
                              .map(String::toLowerCase)
                              .map(String::strip)
                              .orElse("default");

        String nThreadsStr = System.getProperty("JAVALINEER_POOL_THREADS", null);
        Integer nThreads = null;

        try {
            nThreads = Integer.parseInt(nThreadsStr, 10);
        } catch (NumberFormatException ignored) { }

        switch (pool) {
            case "default":
            case "fixed":
                if (nThreads == null) {
                    return newDefaultPool();
                } else {
                    return newFixedPool(nThreads);
                }
            case "debug":
                return new DebugPool();
            case "dynamic":
                return newDynamicPool();
            default:
                throw new RuntimeException("Invalid JAVALINEER_POOL value: " + pool);
        }
    }
}
