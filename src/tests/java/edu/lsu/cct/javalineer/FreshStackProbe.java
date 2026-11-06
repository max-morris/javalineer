package edu.lsu.cct.javalineer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Under -ea, a call that leaves the guard state changed (here, a body count it never takes back) fails the state
     * assertion. The trampoline must still be given back (lent is 0 after, and Pool.join on the caller's own stack
     * waits as usual), and if the call threw too, the caller gets the call's throwable with the AssertionError
     * suppressed under it. If it returned, the caller gets the AssertionError. Returns null if all holds, or what
     * didn't.
     */
    public static String brokenStateIsReported() {
        GuardTask.Trampoline tr = GuardTask.Trampoline.current();
        var thrown = new RuntimeException("expected by TestFreshStack: a call that breaks the guard state");
        Throwable got = null;
        try {
            Guard.callOnFreshStack(() -> {
                GuardTask.Trampoline.current().bodies++;
                throw thrown;
            });
        } catch (Throwable t) {
            got = t;
        } finally {
            tr.bodies--;
        }
        if (got != thrown) {
            return "the call's own throwable did not come back: " + got;
        }
        if (thrown.getSuppressed().length != 1 || !(thrown.getSuppressed()[0] instanceof AssertionError)) {
            return "the AssertionError was not suppressed under the call's throwable: "
                   + java.util.Arrays.toString(thrown.getSuppressed());
        }
        if (tr.lent != 0) {
            return "the trampoline stayed lent after a throwing call (lent " + tr.lent + ")";
        }
        got = null;
        try {
            Guard.callOnFreshStack(() -> GuardTask.Trampoline.current().bodies++);
        } catch (Throwable t) {
            got = t;
        } finally {
            tr.bodies--;
        }
        if (!(got instanceof AssertionError)) {
            return "a returning call that broke the state did not fail with AssertionError: " + got;
        }
        if (tr.lent != 0) {
            return "the trampoline stayed lent after a returning call (lent " + tr.lent + ")";
        }
        if (Pool.join(CompletableFuture.supplyAsync(() -> 5, Pool.getPool())) != 5) {
            return "Pool.join on the caller's stack after the failures";
        }
        return null;
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
