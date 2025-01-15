package edu.lsu.cct.javalineer;

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
}
