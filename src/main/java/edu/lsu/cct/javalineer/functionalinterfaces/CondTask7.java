/* Generated by mkguards.py */
package edu.lsu.cct.javalineer.functionalinterfaces;
import edu.lsu.cct.javalineer.Var;
import edu.lsu.cct.javalineer.CondTask;

public class CondTask7<T1, T2, T3, T4, T5, T6, T7> extends CondTask {
    Var<T1> t1;
    Var<T2> t2;
    Var<T3> t3;
    Var<T4> t4;
    Var<T5> t5;
    Var<T6> t6;
    Var<T7> t7;
    public void set1(Var<T1> t1) { this.t1 = t1; }    public void set2(Var<T2> t2) { this.t2 = t2; }    public void set3(Var<T3> t3) { this.t3 = t3; }    public void set4(Var<T4> t4) { this.t4 = t4; }    public void set5(Var<T5> t5) { this.t5 = t5; }    public void set6(Var<T6> t6) { this.t6 = t6; }    public void set7(Var<T7> t7) { this.t7 = t7; }

    public final CondCheck7<T1, T2, T3, T4, T5, T6, T7> check;
    public CondTask7(CondCheck7<T1, T2, T3, T4, T5, T6, T7> check) {
        this.check = check;
    }

    @Override
    public final void run() {
        if (!done) {
            try {
                done = check.check(t1, t2, t3, t4, t5, t6, t7);
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

