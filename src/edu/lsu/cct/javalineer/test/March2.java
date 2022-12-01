package edu.lsu.cct.javalineer.test;

import java.util.*;
import edu.lsu.cct.javalineer.*;
import edu.lsu.cct.javalineer.functionalinterfaces.CondCheck3;

import java.util.concurrent.atomic.AtomicInteger;

public class March2 implements Runnable {
    final static int N_SEGS = 100;
    static class Segment {
        GuardVar<Segment> left, right;
        int step;
        final int id;
        Segment() { this.id = -1; step = N_SEGS; }
        Segment(int id) { this.id = id; }
        public String toString() {
            return "Seg("+id+","+step+")";
        }
    }
    static class Stepper implements CondCheck3<Segment,Segment,Segment> {
      public boolean check(Var<Segment> l,Var<Segment> ss,Var<Segment> r) {
        final int lstep = l.get().step;
        final int rstep = r.get().step;
        final int step = ss.get().step;
        //new Throwable("runstep").printStackTrace();
        if(lstep >= step && rstep >= step) {
            /*
            if(ss.get().id == 0 || ss.get().id == N_SEGS-1) {
                ss.get().step = 10;
                return true;
            }
            */
            ss.get().step++;
            Stepper stepper = new Stepper();
            Guard.runCondition(l.guardVar(), ss.guardVar(), r.guardVar(), stepper);
            boolean show = ss.get().id == 0;// || ss.get().id == N_SEGS/2;
            if(show) 
                Here.print("id: "+ss.get().id+", step: s="+ss.get().step+"/r="+rstep+"/l="+lstep);
            if(ss.get().id > 0) {
                l.signal();
                if(ss.get().id==0)
                    System.out.print("  left:"+l.get().id);
            }
            if(ss.get().id < N_SEGS-1) {
                r.signal();
                if(show)
                    System.out.print("  right:"+r.get().id);
            }
            if(show)
                System.out.println();
            return true;
        } else {
            //Here.println(" -> id: "+ss.get().id+", step: s="+ss.get().step+"/r="+rstep+"/l="+lstep);
        }
        return false;
    }
  }

    public void run() {
        List<GuardVar<Segment>> line = new ArrayList<>();
        for(int i=0;i<N_SEGS;i++) {
            line.add(new GuardVar<>(new Segment(i)));
        }
        final GuardVar<Segment> send = new GuardVar<>(new Segment());
        final AtomicInteger ai = new AtomicInteger(0);
        for(int i=0;i<N_SEGS;i++) {
            final int ix = i;
            Guard.runGuarded(line.get(i),(s)->{
                int i0 = ix - 1;
                int iN = ix + 1;
                if(i0 < 0)
                    s.get().left = send;
                else
                    s.get().left = line.get(i0);
                if(iN >= line.size())
                    s.get().right = send;
                else
                    s.get().right = line.get(iN);
                int nv = ai.incrementAndGet();
                var rstep = s.get().right;
                var lstep = s.get().left;
                Here.println("nv="+nv);
                if(nv == N_SEGS) {
                    for(int j=0;j<N_SEGS;j++) {
                        Guard.runGuarded(line.get(j),(ss)->{
                            Guard.runCondition(ss.get().left,ss.guardVar(),ss.get().right,new Stepper());
                        });
                    }
                }
            });
        }
      }

      public static void main(String[] args) {
        Test.requireAssert();
        Pool.run(new March2());
        Pool.await();
      }
}
