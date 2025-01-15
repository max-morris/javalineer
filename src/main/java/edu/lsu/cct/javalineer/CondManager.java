package edu.lsu.cct.javalineer;

import java.util.concurrent.atomic.AtomicReference;


public class CondManager {
    AtomicReference<CondLink> head = new AtomicReference<>(null);

    /**
     * Atomically append to the front of the list.
     */
    public void add(CondLink cl) {
        while (true) {
            cl.next.set(head.get());
            if (head.compareAndSet(cl.next.get(), cl))
                break;
        }
    }

    /**
     * Get a reference to the next Condition Link in
     * the chain. If the condition has already succeeded,
     * i.e. it is done, snip it out.
     */
    private CondLink getRef(AtomicReference<CondLink> ref) {
        CondLink r = null;
        while (true) {
            r = ref.get();
            if (r == null)
                return null;
            if (r.cond.task.done) {
                // snip out completed task
                CondLink r2 = r.next.get();
                ref.compareAndSet(r, r2);
            } else
                break;
        }
        return r;
    }

    /**
     * Attempt to run each task in the condition list
     * until one of them succeeds, or we reach the
     * end of the list.
     */
    public void signal() {
        CondLink cl = getRef(head);
        signal(cl);
    }

    private void signal(CondLink cl) {
        if (cl != null) {
            if (cl.cond == null) throw cl.err;
            assert cl.cond.gSet != null;
            final CondLink cf = cl;
            final CondTask task = cl.cond.task;
            Guard.runGuarded(cl.cond.gSet, () -> {
                if (task.done) {
                    signal(getRef(cf.next));
                    return;
                }
                task.run();
                if (!task.done)
                    signal(getRef(cf.next));
            });
        }
    }

    /**
     * Run all tasks in the condition list.
     */
    public void signalAll() {
        CondLink cl = getRef(head);
        while (cl != null) {
            Guard.runGuarded(cl.cond.gSet, cl.cond.task);
            cl = getRef(cl.next);
        }
    }
}
