package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck1;
import edu.lsu.cct.javalineer.functionalinterfaces.GuardTask1;

public class TestBank2 {

    static class Bank extends Guarded {
        int balance = 0;

        boolean withdraw(int a) {
            assert a > 0;
            if(a > balance)
                return false;
            balance -= a;
            return true;
        }

        void deposit(int a) {
            assert a > 0;
            balance += a;
        }
    }

    public static void main(String[] args) {
        Test.requireAssert();

        GuardVar<Bank> a = new GuardVar<>(new Bank());

        for(int i=0;i<1000;i++) {
            Pool.run(()->{
                Guard.runCondition(a,new CondCheck1<>() {
                    public boolean check(Var<Bank> bank) {
                        return bank.get().withdraw(1);
                    }
                });
            });
            Pool.run(()->{
                Guard.runGuarded(a,new GuardTask1<Bank>() {
                    public void run(Var<Bank> bank) {
                        bank.get().deposit(1);
                        bank.get().getGuard().signal();
                    }
                });
            });
        }

        Pool.await();
        int[] out = new int[1];

        a.runGuarded((bank)->{
            out[0] = bank.get().balance;
            assert out[0] == 0;
        });

        Pool.await();

    }
}
