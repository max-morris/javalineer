package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;

/**
 * White-box checks for TestFreshStack, which need Javalineer's package-private guard state: assertLocked and the
 * trampoline loop's slot. Lives in the tests source set, in the library's package.
 */
public final class FreshStackProbe {
    private FreshStackProbe() { }

    /** Inside a guarded body: on a fresh stack, the held set is the caller's, and assertLocked passes on it. */
    public static boolean heldIsLentAndLocked() {
        GuardSet below = GuardTask.GUARDS_HELD.get();
        if (below == null) {
            return false;
        }
        return Guard.callOnFreshStack(() -> {
            GuardSet here = GuardTask.GUARDS_HELD.get();
            GuardTask.assertLocked(here);
            return here == below && GuardTask.Trampoline.current().lent > 0;
        });
    }

    /**
     * A successor offered to the trampoline's slot during a call on a fresh stack is in the caller's slot when the call
     * returns, and runs after the call, as the loop runs it after its current handoff returns. The thread is put in
     * the state of a loop running one acquisition (as Trampoline.loop sets it), since from the public API an offer
     * into the slot comes only from the release of that acquisition, which happens below any user code; the call
     * then makes that release's offer. Returns the order of events, which should be "call,successor".
     */
    public static String slotAcrossCall() {
        List<String> order = new ArrayList<>();
        Guard g = new Guard();
        Guard h = new Guard();
        GuardTask running = new GuardTask(g, GuardSet.of(g));
        GuardTask succ = new GuardTask(h, () -> order.add("successor"), GuardSet.of(h));
        GuardTask.Trampoline tr = GuardTask.Trampoline.current();
        if (tr.running != null || tr.slot != null) {
            return "the thread was already running a loop";
        }
        tr.running = running;
        tr.slot = null;
        try {
            Guard.callOnFreshStack(() -> {
                GuardTask.Trampoline.offer(succ, List.of(running));
                order.add("call");
                return null;
            });
            if (tr.slot != succ) {
                return "the slot was not the caller's after the call: " + tr.slot;
            }
            // As Trampoline.loop does when the handoff returns.
            GuardTask next = tr.slot;
            tr.running = next;
            tr.slot = null;
            next.run();
        } finally {
            tr.running = null;
            tr.slot = null;
        }
        return String.join(",", order);
    }

    /** The trampoline is no longer lent once the call returns, and the helper thread drops it. */
    public static boolean notLentAfter() {
        GuardTask.Trampoline tr = GuardTask.Trampoline.current();
        GuardTask.Trampoline[] seen = new GuardTask.Trampoline[1];
        Guard.callOnFreshStack(() -> {
            seen[0] = GuardTask.Trampoline.current();
            return null;
        });
        return seen[0] == tr && tr.lent == 0;
    }
}
