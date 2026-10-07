package jvmprobe249.transform;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Wraps one eligible method body so that:
 * <ul>
 *   <li>{@code CallStack.enter(...)} runs first, before any user code;</li>
 *   <li>every {@code *return} first settles normally while preserving the
 *       original return value;</li>
 *   <li>a catch-all handler settles abnormally and rethrows the original
 *       throwable, preserving user catch/finally semantics.</li>
 * </ul>
 */
final class MethodInstrumenter {

    private static final String CALL_STACK = "jvmprobe249/runtime/CallStack";

    private MethodInstrumenter() {
    }

    static boolean isEligible(int access, String name) {
        if ((access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return false;
        }
        return !"<init>".equals(name) && !"<clinit>".equals(name);
    }

    static void instrument(ClassNode owner, MethodNode method) {
        int frameSlot = allocateFrameSlot(method);
        int valueSlot = frameSlot + 1;

        LabelNode bodyStart = new LabelNode();
        LabelNode bodyEnd = new LabelNode();
        LabelNode handler = new LabelNode();

        InsnList prefix = new InsnList();
        prefix.add(new LdcInsnNode(owner.name));
        prefix.add(new LdcInsnNode(method.name));
        prefix.add(new LdcInsnNode(method.desc));
        prefix.add(new MethodInsnNode(INVOKESTATIC, CALL_STACK, "enter",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                false));
        prefix.add(new VarInsnNode(ASTORE, frameSlot));
        prefix.add(bodyStart);
        method.instructions.insert(prefix);

        List<AbstractInsnNode> exits = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
                exits.add(insn);
            }
        }

        for (AbstractInsnNode exit : exits) {
            InsnList settle = new InsnList();
            if (exit.getOpcode() == ATHROW) {
                settle.add(new VarInsnNode(ASTORE, valueSlot));
                settle.add(new VarInsnNode(ALOAD, frameSlot));
                settle.add(new InsnNode(ICONST_1));
                settle.add(invokeExit());
                settle.add(new VarInsnNode(ALOAD, valueSlot));
            } else {
                storeReturnValue(settle, exit.getOpcode(), valueSlot);
                settle.add(new VarInsnNode(ALOAD, frameSlot));
                settle.add(new InsnNode(ICONST_0));
                settle.add(invokeExit());
                loadReturnValue(settle, exit.getOpcode(), valueSlot);
            }
            method.instructions.insertBefore(exit, settle);
        }

        InsnList tail = new InsnList();
        tail.add(bodyEnd);
        tail.add(handler);
        tail.add(new VarInsnNode(ASTORE, valueSlot));
        tail.add(new VarInsnNode(ALOAD, frameSlot));
        tail.add(new InsnNode(ICONST_1));
        tail.add(invokeExit());
        tail.add(new VarInsnNode(ALOAD, valueSlot));
        tail.add(new InsnNode(ATHROW));
        method.instructions.add(tail);

        method.tryCatchBlocks.add(new TryCatchBlockNode(
                bodyStart, bodyEnd, handler, "java/lang/Throwable"));

        method.maxStack = 0;
        method.maxLocals = 0;
    }

    private static int allocateFrameSlot(MethodNode method) {
        int slot = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            slot += arg.getSize();
        }
        if (slot < method.maxLocals) {
            slot = method.maxLocals;
        }
        return slot;
    }

    private static MethodInsnNode invokeExit() {
        return new MethodInsnNode(INVOKESTATIC, CALL_STACK, "exit",
                "(Ljava/lang/Object;Z)V", false);
    }

    private static void storeReturnValue(InsnList list, int opcode, int slot) {
        switch (opcode) {
            case IRETURN -> list.add(new VarInsnNode(ISTORE, slot));
            case LRETURN -> list.add(new VarInsnNode(LSTORE, slot));
            case FRETURN -> list.add(new VarInsnNode(FSTORE, slot));
            case DRETURN -> list.add(new VarInsnNode(DSTORE, slot));
            case ARETURN -> list.add(new VarInsnNode(ASTORE, slot));
            default -> { }
        }
    }

    private static void loadReturnValue(InsnList list, int opcode, int slot) {
        switch (opcode) {
            case IRETURN -> list.add(new VarInsnNode(ILOAD, slot));
            case LRETURN -> list.add(new VarInsnNode(LLOAD, slot));
            case FRETURN -> list.add(new VarInsnNode(FLOAD, slot));
            case DRETURN -> list.add(new VarInsnNode(DLOAD, slot));
            case ARETURN -> list.add(new VarInsnNode(ALOAD, slot));
            default -> { }
        }
    }
}
