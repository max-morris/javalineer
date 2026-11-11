package edu.lsu.cct.javalineer;

/**
 * White-box access for TestFreshStackWait: the test hook of FreshStack.call, through which a throw is injected on the
 * caller at a named point of the handoff, and the trampoline's give-back. Lives in the tests source set, in the
 * library's package.
 */
public final class FreshStackWaitProbe {
    private FreshStackWaitProbe() { }

    /** Called with the point's name; a throw from it is a throw on the caller at that point. */
    public interface Injector {
        void at(String point);
    }

    private static final String[] NAMES = {
        "AFTER_START", "BEFORE_UNPARK", "AFTER_UNPARK", "BEFORE_PARK", "AFTER_PARK", "AFTER_INTERRUPTED"
    };

    static {
        assert NAMES[FreshStack.AFTER_START].equals("AFTER_START")
               && NAMES[FreshStack.BEFORE_UNPARK].equals("BEFORE_UNPARK")
               && NAMES[FreshStack.AFTER_UNPARK].equals("AFTER_UNPARK")
               && NAMES[FreshStack.BEFORE_PARK].equals("BEFORE_PARK")
               && NAMES[FreshStack.AFTER_PARK].equals("AFTER_PARK")
               && NAMES[FreshStack.AFTER_INTERRUPTED].equals("AFTER_INTERRUPTED");
    }

    /** The points, by name, in the order of the handoff. */
    public static String[] points() {
        return NAMES.clone();
    }

    /**
     * Installs inj as the hook, or removes the hook if null. A plain field: set it on the thread that makes the calls,
     * or before handing work to the threads that do.
     */
    public static void setInjector(Injector inj) {
        FreshStack.hook = inj == null ? null : p -> inj.at(NAMES[p]);
    }

    /**
     * Retires every helper now idle, as its idle timeout would but at once: takes it off the idle stack (as a caller
     * would) and marks it abandoned, so that it ends the next time it looks for a call. The next calls then start new
     * helpers. Returns how many were retired.
     */
    public static int retireIdleHelpers() {
        int n = 0;
        FreshStack.Helper h;
        while ((h = FreshStack.IDLE.pollFirst()) != null) {
            h.abandoned = true;
            java.util.concurrent.locks.LockSupport.unpark(h.thread);
            n++;
        }
        return n;
    }

    /** How many fresh stacks this thread's trampoline is lent to now. */
    public static int lent() {
        return GuardTask.Trampoline.current().lent;
    }

    /**
     * giveBack's assertion fires after the cleanup, not instead of it: on a scratch thread, a trampoline is lent, the
     * thread's trampoline is then replaced (what the assertion detects), and giveBack must still decrement lent and
     * remove the thread's entry, then fail with an AssertionError. Returns null if all holds, or what didn't.
     */
    public static String giveBackCleansUpWhenItsAssertionFires() throws InterruptedException {
        String[] result = new String[1];
        Thread t = new Thread(() -> {
            var tr = new GuardTask.Trampoline();
            var other = new GuardTask.Trampoline();
            GuardTask.Trampoline.lend(tr);
            GuardTask.Trampoline.lend(other);
            Throwable got = null;
            try {
                GuardTask.Trampoline.giveBack(tr);
            } catch (Throwable e) {
                got = e;
            }
            if (!(got instanceof AssertionError)) {
                result[0] = "giveBack of a replaced trampoline did not fail its assertion: " + got;
            } else if (tr.lent != 0) {
                result[0] = "the assertion left the trampoline counted as lent (lent " + tr.lent + ")";
            } else if (GuardTask.Trampoline.current() == other) {
                result[0] = "the assertion left the thread's trampoline entry in place";
            }
        });
        t.start();
        t.join();
        return result[0];
    }
}
