package jvmprobe249.runtime;

import jvmprobe249.MethodId;

/**
 * Per-thread call stack plus the registry that records completions. Every
 * thread owns an independent stack, so recursion and mutual recursion settle
 * level by level and cross-thread parent/child accounting never happens.
 */
public final class CallStack {

    private static final ProbeRegistry REGISTRY = new ProbeRegistry();

    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final ThreadLocal<CallFrame> TOP =
            ThreadLocal.withInitial(() -> null);

    private CallStack() {
    }

    public static ProbeRegistry registry() {
        return REGISTRY;
    }

    public static int inFlightCount() {
        return IN_FLIGHT.get();
    }

    /** Invoked by instrumented bytecode on method entry. */
    public static Object enter(String className, String methodName,
                               String descriptor) {
        CallFrame parent = TOP.get();
        MethodAccumulator accumulator =
                REGISTRY.accumulatorFor(new MethodId(className, methodName, descriptor));
        CallFrame frame = new CallFrame(accumulator, parent, System.nanoTime());
        TOP.set(frame);
        IN_FLIGHT.incrementAndGet();
        return frame;
    }

    /** Invoked by instrumented bytecode on every normal or abnormal exit. */
    public static void exit(Object token, boolean exceptional) {
        if (!(token instanceof CallFrame frame) || frame.settled) {
            return;
        }
        CallFrame current = TOP.get();
        if (current != frame) {
            return;
        }
        frame.settled = true;
        long inclusive = System.nanoTime() - frame.startNanos;
        long self = inclusive - frame.childNanos;
        if (frame.parent != null) {
            frame.parent.childNanos += inclusive;
        }
        TOP.set(frame.parent);
        try {
            REGISTRY.record(frame.accumulator, inclusive, self, exceptional);
        } finally {
            IN_FLIGHT.decrementAndGet();
        }
    }
}
