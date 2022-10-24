package org.tudalgo.algoutils.transform;

import org.objectweb.asm.*;
import org.sourcegrade.jagr.api.testing.ClassTransformer;

public class AccessTransformer implements ClassTransformer {

    @Override
    public String getName() {
        return "AccessTransformer";
    }

    @Override
    public void transform(ClassReader reader, ClassWriter writer) {
        reader.accept(new CV(Opcodes.ASM9, writer), 0);
    }

    private static int makePublic(int access) {
        return access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC & ~Opcodes.ACC_FINAL;
    }

    private static class CV extends ClassVisitor {
        public CV(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, makePublic(access), name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return super.visitMethod(makePublic(access), name, descriptor, signature, exceptions);
        }
    }
}
