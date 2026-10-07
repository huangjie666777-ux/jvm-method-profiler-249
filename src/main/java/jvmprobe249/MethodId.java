package jvmprobe249;

import java.util.Objects;

/**
 * Immutable identity of a profiled method: internal class name, method name
 * and JVM method descriptor (e.g. {@code (II)I}).
 */
public final class MethodId {

    private final String className;
    private final String methodName;
    private final String descriptor;

    public MethodId(String className, String methodName, String descriptor) {
        this.className = Objects.requireNonNull(className, "className");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public String className() {
        return className;
    }

    public String methodName() {
        return methodName;
    }

    public String descriptor() {
        return descriptor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MethodId)) {
            return false;
        }
        MethodId that = (MethodId) other;
        return className.equals(that.className)
                && methodName.equals(that.methodName)
                && descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, descriptor);
    }

    @Override
    public String toString() {
        return className + "." + methodName + descriptor;
    }
}
