package edu.lsu.cct.javalineer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * The tail of a guard's task queue, alone on its cache line: every issuer's getAndSet writes it, so nothing else that
 * is read or written per task may share its line. A plain AtomicReference is 16 bytes, allocated right after the
 * guard and its locked flag, which the running task writes twice per task. The padding (the int and eight longs
 * before, eight longs after; superclass fields come first, and with a 12-byte object header the int fills the gap
 * after it so that no later field can) keeps at least 64 bytes of this object on either side of the value. That bound
 * holds under every layout checked, including 8- and 16-byte headers on JDK 21 and 25.
 */
final class GuardTail extends GuardTailValue {
    long q0, q1, q2, q3, q4, q5, q6, q7;

    GuardTask getAndSet(GuardTask t) {
        return (GuardTask) VALUE.getAndSet(this, t);
    }
}

abstract class GuardTailPad {
    int pi;
    long p0, p1, p2, p3, p4, p5, p6, p7;
}

abstract class GuardTailValue extends GuardTailPad {
    volatile GuardTask value;
    static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(GuardTailValue.class, "value", GuardTask.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
