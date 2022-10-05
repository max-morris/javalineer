
package edu.lsu.cct.javalineer;

public class CondTask5<T1, T2, T3, T4, T5> extends CondTask {
    Var<T1> t1;
    Var<T2> t2;
    Var<T3> t3;
    Var<T4> t4;
    Var<T5> t5;
    public void set1(Var<T1> t1) { this.t1 = t1; }    public void set2(Var<T2> t2) { this.t2 = t2; }    public void set3(Var<T3> t3) { this.t3 = t3; }    public void set4(Var<T4> t4) { this.t4 = t4; }    public void set5(Var<T5> t5) { this.t5 = t5; }

    public final CondCheck5<T1, T2, T3, T4, T5> check;
    public CondTask5(CondCheck5<T1, T2, T3, T4, T5> check) {
        this.check = check;
    }

    public final void run() {
        if (!done) {
            try {
                done = check.check(t1, t2, t3, t4, t5);
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

