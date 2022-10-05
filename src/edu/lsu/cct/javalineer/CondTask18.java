
package edu.lsu.cct.javalineer;

public class CondTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> extends CondTask {
    Var<T1> t1;
    Var<T2> t2;
    Var<T3> t3;
    Var<T4> t4;
    Var<T5> t5;
    Var<T6> t6;
    Var<T7> t7;
    Var<T8> t8;
    Var<T9> t9;
    Var<T10> t10;
    Var<T11> t11;
    Var<T12> t12;
    Var<T13> t13;
    Var<T14> t14;
    Var<T15> t15;
    Var<T16> t16;
    Var<T17> t17;
    Var<T18> t18;
    public void set1(Var<T1> t1) { this.t1 = t1; }    public void set2(Var<T2> t2) { this.t2 = t2; }    public void set3(Var<T3> t3) { this.t3 = t3; }    public void set4(Var<T4> t4) { this.t4 = t4; }    public void set5(Var<T5> t5) { this.t5 = t5; }    public void set6(Var<T6> t6) { this.t6 = t6; }    public void set7(Var<T7> t7) { this.t7 = t7; }    public void set8(Var<T8> t8) { this.t8 = t8; }    public void set9(Var<T9> t9) { this.t9 = t9; }    public void set10(Var<T10> t10) { this.t10 = t10; }    public void set11(Var<T11> t11) { this.t11 = t11; }    public void set12(Var<T12> t12) { this.t12 = t12; }    public void set13(Var<T13> t13) { this.t13 = t13; }    public void set14(Var<T14> t14) { this.t14 = t14; }    public void set15(Var<T15> t15) { this.t15 = t15; }    public void set16(Var<T16> t16) { this.t16 = t16; }    public void set17(Var<T17> t17) { this.t17 = t17; }    public void set18(Var<T18> t18) { this.t18 = t18; }

    public final CondCheck18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> check;
    public CondTask18(CondCheck18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> check) {
        this.check = check;
    }

    public final void run() {
        if (!done) {
            try {
                done = check.check(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18);
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

