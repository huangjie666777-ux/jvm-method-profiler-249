package jvmprobe249;

import java.util.Map;
import jvmprobe249.runtime.CallStack;
import jvmprobe249.transform.ProbeClassTransformer;

/**
 * Public entry point of the profiling SDK.
 */
public final class Probe {

    private static final ProbeClassTransformer TRANSFORMER = new ProbeClassTransformer();

    private Probe() {
    }

    /**
     * Returns instrumented bytes for the given class. The class is never
     * initialized. Unsupported or invalid classes result in
     * {@link jvmprobe249.transform.ClassTransformException}. Transforming an
     * already transformed class returns its bytes unchanged.
     */
    public static byte[] transform(byte[] classBytes, ClassLoader resolvingLoader) {
        return TRANSFORMER.transform(classBytes, resolvingLoader);
    }

    /** Immutable, consistent snapshot keyed by class/method/descriptor. */
    public static Map<MethodId, MethodStats> snapshot() {
        return CallStack.registry().snapshot();
    }

    /** Number of method activations currently in flight across all threads. */
    public static int inFlightCount() {
        return CallStack.inFlightCount();
    }

    /** Clears statistics; throws if any profiled call is still in flight. */
    public static void reset() {
        CallStack.registry().reset(CallStack.inFlightCount());
    }
}
