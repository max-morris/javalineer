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

    Var<?> getVar(int idx) {
        return vars[idx];
    }

    public CompletableFuture<Void> runCondition(final T task) {
        Guard.runGuarded(gSet, () -> {
            task.run();
            if (!task.done) {
                enq(task);
            }
        });

        return task.fut;
    }

    public void signal() {
        snapshot().thenAccept(tasks -> {
            Guard.runGuarded(gSet, () -> {
                for (var task : tasks) {
                    if (!task.done) {
                        task.run();
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
                    if (!task.done) {
                        task.run();
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

    private CompletableFuture<List<T>> snapshot() {
        var tasksFut = new CompletableFuture<List<T>>();

        tasks.runGuarded(tasksVar -> {
            var tasks = tasksVar.get();
            var taskList = new ArrayList<T>(tasks.size());

            for (var task : tasks) {
                if (!task.done) {
                    taskList.add(task);
                }
            }

            tasksVar.set(taskList);
            tasksFut.complete(taskList);
        });

        return tasksFut;
    }
}
