
package edu.lsu.cct.javalineer.functionalinterfaces;

import edu.lsu.cct.javalineer.CondTask;
import edu.lsu.cct.javalineer.Var;
import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck4;

public class CondTask4<T1, T2, T3, T4> extends CondTask {
    Var<T1> t1;
    Var<T2> t2;
    Var<T3> t3;
    Var<T4> t4;
    public void set1(Var<T1> t1) { this.t1 = t1; }    public void set2(Var<T2> t2) { this.t2 = t2; }    public void set3(Var<T3> t3) { this.t3 = t3; }    public void set4(Var<T4> t4) { this.t4 = t4; }

    public final CondCheck4<T1, T2, T3, T4> check;
    public CondTask4(CondCheck4<T1, T2, T3, T4> check) {
        this.check = check;
    }

    public final void run() {
        if (!done) {
            try {
                done = check.check(t1, t2, t3, t4);
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

