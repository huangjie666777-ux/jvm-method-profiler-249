package jvmprobe249.transform;

import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_MODULE;
import static org.objectweb.asm.Opcodes.ASM9;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Parses class bytes, validates the supported class kind and rewrites every
 * eligible method with enter/exit probes. No class is initialized or executed
 * during transformation.
 */
public final class ProbeClassTransformer {

    private static final String PROBED_MARKER = "jvmprobe249/runtime/Probed";
    private static final int JAVA17_MAJOR = 61;

    public byte[] transform(byte[] classBytes, ClassLoader loader) {
        if (classBytes == null || classBytes.length == 0) {
            throw new ClassTransformException("class byte array is null or empty");
        }

        ClassNode classNode = new ClassNode(ASM9);
        ClassReader reader;
        try {
            reader = new ClassReader(classBytes);
            reader.accept(classNode, 0);
        } catch (RuntimeException e) {
            throw new ClassTransformException("invalid class bytecode", e);
        }

        if (classNode.name == null) {
            throw new ClassTransformException("invalid class bytecode: missing class name");
        }
        if (isSdkClass(classNode.name)) {
            return classBytes;
        }
        if ((classNode.access & ACC_MODULE) != 0 || "module-info".equals(classNode.name)) {
            throw new ClassTransformException(
                    "unsupported class kind: module " + classNode.name);
        }
        if ((classNode.access & (ACC_INTERFACE | ACC_ANNOTATION)) != 0) {
            throw new ClassTransformException(
                    "unsupported class kind: interface or annotation " + classNode.name);
        }
        int majorVersion = classNode.version & 0xFFFF;
        if (majorVersion != JAVA17_MAJOR) {
            throw new ClassTransformException(
                    "unsupported class file version " + majorVersion + " for "
                            + classNode.name + "; only Java 17 (61) is supported");
        }
        if (classNode.interfaces != null && classNode.interfaces.contains(PROBED_MARKER)) {
            return classBytes;
        }

        List<MethodNode> methods = classNode.methods;
        if (methods != null) {
            for (MethodNode method : methods) {
                if (MethodInstrumenter.isEligible(method.access, method.name)) {
                    MethodInstrumenter.instrument(classNode, method);
                }
            }
        }

        if (classNode.interfaces == null) {
            classNode.interfaces = new java.util.ArrayList<>();
        }
        classNode.interfaces.add(PROBED_MARKER);

        ClassWriter writer = new SafeClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, loader);
        try {
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException e) {
            throw new ClassTransformException(
                    "failed to generate valid bytecode for " + classNode.name, e);
        }
    }

    private static boolean isSdkClass(String internalName) {
        if (internalName.startsWith("jvmprobe249/runtime/")
                || internalName.startsWith("jvmprobe249/transform/")) {
            return true;
        }
        String prefix = "jvmprobe249/";
        return internalName.startsWith(prefix)
                && internalName.indexOf('/', prefix.length()) < 0;
    }
}
