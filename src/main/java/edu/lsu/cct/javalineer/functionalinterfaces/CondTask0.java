package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.CondTask;

public class CondTask0 extends CondTask {
    public final CondCheck0 check;

    public CondTask0(CondCheck0 check) {
        this.check = check;
    }

    public final void run() {
        if (!done) {
            try {
                done = check.check();
                if (done) {
                    fut.complete(null);
                }
            } catch (Exception e) {
                done = true;
                fut.completeExceptionally(e);
            }
        }
    }
}
