package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A condition: tasks which wait until a predicate over the condition's guarded state holds, and are run again by
 * {@link #signal} or {@link #signalAll}.
 * <p>
 * The waiting tasks are plain state of the condition, read and written only in bodies which hold the condition's own
 * guards, {@code gSet}, like the state the predicates read. A task's first check and, if it fails, its registration
 * are one body; a signal is one body which runs the waiting tasks again. A check and a signal never overlap, so a
 * signal whose body runs after a write either finds the task registered, when the check's body came first, or came
 * first itself, and then the check's body starts after the signal's and so after the write, and reads the new state.
 * There is no window in which the task is neither registered nor about to read the new state.
 * <p>
 * So a signal wakes a task for every write which is done before the signal's body runs. That is every write which
 * happens-before the call to signal (a signal after the write inside the writing body, in a continuation of its
 * future, after joining it, or after an unguarded write such as an atomic's), and also a guarded write merely issued
 * before the call to signal (on the same thread, or on another before it in happens-before order),
 * {@code gv.runGuarded(v -> v.set(x)); cond.signal();}, when the write's lowest guard is one of {@code gSet}: the write's acquisition is queued on that guard when it is issued, the signal's comes behind it there,
 * and the write holds that guard until its body ends. A write whose lowest guard is not one of {@code gSet}, still
 * queued when the signal runs, comes after it, and needs a signal of its own (issued, for instance, inside it).
 * <p>
 * It did not hold when the list had a guard of its own: the registration was a second body, on that guard, issued
 * at the end of the check, and a signal's snapshot of the list was a body on the same guard which could run between
 * the check's read and the registration. A signal after a guarded write ({@code gv.runGuarded(v -> v.set(x));
 * cond.signal();}) then found the list empty while the check still held {@code gSet}, and the task waited forever
 * with its predicate true (Guardian proof notes, discrepancies.md hole 68).
 * <p>
 * A signal issued inside a body which holds {@code gSet} is a nested acquisition of a held guard, so it queues behind
 * that body and runs when the body has released; so is any signal, runCondition or signalAll which a task's check,
 * or a continuation of its future, issues while a signal is running the list. Nothing touches the list during a
 * walk but the walk itself.
 */
public final class CondContext<T extends CondTask> {
    private final GuardSet gSet;
    /**
     * The waiting tasks, in the order in which their first checks failed. Read and written only in bodies which hold
     * {@code gSet}: the guards' handoff orders every access and publishes every write, so it needs no lock and no
     * guard of its own. A task leaves it in the body in which it became done (its predicate held, or it threw), so
     * between bodies it holds no task which is done (unless the caller handed one task to runCondition twice, or a
     * subclass set {@code done} itself; a walk drops those without running them).
     */
    private final ArrayList<T> waiters = new ArrayList<>();
    private final Var<?>[] vars;

    private CondContext(GuardSet gSet, Var<?>... vars) {
        // With no guards, an acquisition of gSet runs its body at once with no exclusion at all, and the list would
        // be raced. Such a condition's predicates read no guarded state, so a private guard serializes its checks
        // and signals and changes nothing else.
        this.gSet = gSet.size() == 0 ? GuardSet.of(new Guard()) : gSet;
        this.vars = vars;
    }

    private CondContext(GuardSet gSet) {
        this(gSet, new Var[0]);
    }

    static <T extends CondTask> CondContext<T> newCond(GuardSet guards) {
        return new CondContext<>(guards);
    }

    static <T extends CondTask> CondContext<T> newCond(Guard... guards) {
        return new CondContext<>(GuardSet.of(guards));
    }

    static <T extends CondTask> CondContext<T> newCond(GuardVar<?>... guardVars) {
        var vars = Arrays.stream(guardVars)
                         .map(gv -> gv.var)
                         .toArray(Var[]::new);
        return new CondContext<>(GuardSet.of(guardVars), vars);
    }

    static <T extends CondTask> CondContext<T> newCondCached(Guard... guards) {
        return new CondContext<>(GuardSet.ofCached(guards));
    }

    static <T extends CondTask> CondContext<T> newCondCached(GuardVar<?>... guardVars) {
        var vars = Arrays.stream(guardVars)
                         .map(gv -> gv.var)
                         .toArray(Var[]::new);
        return new CondContext<>(GuardSet.ofCached(guardVars), vars);
    }

    Var<?> getVar(int idx) {
        return vars[idx];
    }

    /**
     * Runs the task's check under the condition's guards; if it is not done, the task waits in the same body, so no
     * signal can come between the failed check and the registration.
     *
     * @return The task's future, which completes when its check returns true, or exceptionally if it throws.
     */
    public CompletableFuture<Void> runCondition(final T task) {
        Guard.runGuarded(gSet, () -> {
            task.runCapturing();
            if (!task.done) {
                waiters.add(task);
            }
        });
        // For a deadlock report (Pool.join): a condition task whose predicate never holds waits forever.
        Pool.trackPending(task.fut, "condition task", this);

        return task.fut;
    }

    /**
     * Runs the waiting tasks again, in the order in which they started waiting, until one is done (its check
     * returned true, or it threw). One acquisition of the condition's guards; inside a body which holds them, it runs
     * after that body.
     */
    public void signal() {
        Guard.runGuarded(gSet, this::rerunOne);
    }

    /**
     * Runs every waiting task again, in the order in which they started waiting. One acquisition of the condition's
     * guards; inside a body which holds them, it runs after that body.
     */
    public void signalAll() {
        Guard.runGuarded(gSet, this::rerunAll);
    }

    // Under gSet. A task which becomes done leaves the list here and then; the walk stops at the first.
    private void rerunOne() {
        var ws = waiters;
        for (int i = 0; i < ws.size(); ) {
            T task = ws.get(i);
            if (task.done) {
                ws.remove(i);
                continue;
            }
            task.runCapturing();
            if (task.done) {
                ws.remove(i);
                return;
            }
            i++;
        }
    }

    // Under gSet. The list is compacted once, after the walk, so the walk never shifts what it has yet to visit.
    private void rerunAll() {
        var ws = waiters;
        try {
            for (int i = 0, n = ws.size(); i < n; i++) {
                T task = ws.get(i);
                if (!task.done) {
                    task.runCapturing();
                }
            }
        } finally {
            ws.removeIf(task -> task.done);
        }
    }
}
