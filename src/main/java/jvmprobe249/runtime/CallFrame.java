package jvmprobe249.runtime;

/**
 * One in-flight method activation on a thread's call stack. Instances are
 * created by instrumented bytecode on method entry and stored in a local
 * variable; business code never sees them.
 */
final class CallFrame {

    final MethodAccumulator accumulator;
    final CallFrame parent;
    final long startNanos;

    long childNanos;
    boolean settled;

    CallFrame(MethodAccumulator accumulator, CallFrame parent, long startNanos) {
        this.accumulator = accumulator;
        this.parent = parent;
        this.startNanos = startNanos;
    }
}
