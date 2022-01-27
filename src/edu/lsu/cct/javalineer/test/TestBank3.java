package edu.lsu.cct.javalineer.test;

import edu.lsu.cct.javalineer.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBank3 {
    static AtomicInteger wc = new AtomicInteger(0), tc = new AtomicInteger(0), dc = new AtomicInteger(0);

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
        GuardVar<Bank> b = new GuardVar<>(new Bank());

        for(int i=0;i<1000;i++) {
            Pool.run(()->{
                Guard.runCondition(a,(Var<Bank> bank)->{
                        boolean b2 = bank.get().withdraw(1);
                        if(b2) wc.getAndIncrement();
                        return b2;
                });
            });
            Pool.run(()->{
                Guard.runCondition(a,b,(Var<Bank> banka,Var<Bank> bankb)->{
                    if(bankb.get().withdraw(1)) {
                        banka.get().deposit(1);
                        banka.signal();
                        tc.getAndIncrement();
                        return true;
                    } else {
                        return false;
                    }
                });
            });
            Pool.run(()->{
                Guard.runGuarded(b,(Var<Bank> bank)->{
                        bank.get().deposit(1);
                        bank.signal();
                        dc.getAndIncrement();
                });
            });
        }

        Pool.await();
        int[] out = new int[1];
        System.out.println("wc="+wc+", tc="+tc+", dc="+dc);

        a.runGuarded((bank)->{
            out[0] = bank.get().balance;
            assert out[0] == 0 : out[0];
        });

        Pool.await();

    }
}
