package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

public class GuardVar<T> extends Guard {
    final Var<T> var;

    public GuardVar(T t) {
        var = new Var<>(t, this);

        if (t instanceof Guarded) {
            Guarded g = (Guarded) t;
            g.setGuard(this);
        }
    }

    public void runGuarded(Consumer<Var<T>> c) {
        runGuarded(() -> c.accept(var));
    }

    public static void runGuarded(List<GuardVar<?>> guards, final Consumer<List<Var<?>>> vars) {
        TreeSet<Guard> ts = new TreeSet<>();
        List<Var<?>> result = new ArrayList<>();

        for (GuardVar<?> gv : guards) {
            ts.add(gv);
            result.add(gv.var);
        }

        Runnable r = () -> vars.accept(result);
        Guard.runGuarded(ts, r);
    }
}
