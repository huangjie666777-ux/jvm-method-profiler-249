package jvmprobe249.transform;

import org.objectweb.asm.ClassWriter;

/**
 * ClassWriter that resolves common super classes through the loader used to
 * parse the transformed class. Resolution failures never fail the transform:
 * they fall back to {@code java/lang/Object} as a conservative answer.
 */
final class SafeClassWriter extends ClassWriter {

    private final ClassLoader loader;

    SafeClassWriter(int flags, ClassLoader loader) {
        super(flags);
        this.loader = loader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            Class<?> class1 = Class.forName(type1.replace('/', '.'), false, loader);
            Class<?> class2 = Class.forName(type2.replace('/', '.'), false, loader);
            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            }
            do {
                class1 = class1.getSuperclass();
            } while (!class1.isAssignableFrom(class2));
            return class1.getName().replace('.', '/');
        } catch (Throwable ignored) {
            return "java/lang/Object";
        }
    }
}
