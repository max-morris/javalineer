package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CondContext<T extends CondTask> {
    private final GuardSet gSet;
    private final GuardVar<List<T>> tasks;
    private final Var<?>[] vars;

    private CondContext(GuardSet gSet, Var<?>... vars) {
        this.gSet = gSet;
        this.tasks = new GuardVar<>(new ArrayList<>());
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

    public CompletableFuture<Void> runCondition(final T task) {
        Guard.runGuarded(gSet, () -> {
            task.runCapturing();
            if (!task.done) {
                enq(task);
            }
        });
        // For a deadlock report (Pool.join): a condition task whose predicate never holds waits forever.
        Pool.trackPending(task.fut, "condition task", this);

        return task.fut;
    }

    public void signal() {
        snapshot().thenAccept(tasks -> {
            Guard.runGuarded(gSet, () -> {
                for (var task : tasks) {
                    if (task != null && !task.done) {
                        task.runCapturing();
                        if (task.done) {
                            break;
                        }
                    }
                }
            });
        });
    }

    public void signalAll() {
        snapshot().thenAccept(tasks -> {
            Guard.runGuarded(gSet, () -> {
                for (var task : tasks) {
                    if (task != null && !task.done) {
                        task.runCapturing();
                    }
                }
            });
        });
    }

    private void enq(T task) {
        tasks.runGuarded(tasks -> {
            tasks.get().add(task);
        });
    }

    /**
     * The waiters as they are now, for one signal or signalAll: a list of the tasks which are not done, in
     * order, possibly with nulls (to be skipped) in place of tasks which were done. No one writes to it after it
     * is handed out.
     */
    private CompletableFuture<List<T>> snapshot() {
        var tasksFut = new CompletableFuture<List<T>>();

        tasks.runGuarded(tasksVar -> {
            var tasks = tasksVar.get();
            var taskList = new ArrayList<T>(tasks.size());

            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                if (task.done) {
                    tasks.set(i, null);
                } else {
                    taskList.add(task);
                }
            }

            tasksVar.set(taskList);
            // taskList is now the live waiter list: enq appends to it under the tasks guard. The future's
            // consumers (signal, signalAll) iterate under gSet, a different guard, so they must never see
            // taskList itself, or a late enq could race the iteration (ConcurrentModificationException or a
            // skipped waiter, i.e. a lost wakeup). Hand out the replaced list, tasks, instead of a copy of
            // taskList: enq and snapshot reach the live list only through tasksVar, under the tasks guard, so
            // from here on no one writes to tasks, and every write to it (the appends of earlier enqs, the
            // nulls above) happens before this completion. With the done tasks nulled out it holds exactly the
            // tasks of taskList, in the same order, so the consumers see the same waiters as with a copy.
            tasksFut.complete(tasks);
        });

        return tasksFut;
    }
}
