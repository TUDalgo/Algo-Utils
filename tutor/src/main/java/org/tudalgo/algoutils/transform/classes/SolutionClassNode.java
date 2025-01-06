package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.*;
import org.tudalgo.algoutils.transform.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.util.headers.ClassHeader;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * A class node for recording bytecode instructions of solution classes.
 * @author Daniel Mangold
 */
public class SolutionClassNode extends ClassNode {

    private final TransformationContext transformationContext;
    private final String className;
    private ClassHeader classHeader;
    private final Map<FieldHeader, FieldNode> fields = new HashMap<>();
    private final Map<MethodHeader, MethodNode> methods = new HashMap<>();

    /**
     * Constructs a new {@link SolutionClassNode} instance.
     *
     * @param className the name of the solution class
     */
    public SolutionClassNode(TransformationContext transformationContext, String className) {
        super(Opcodes.ASM9);
        this.transformationContext = transformationContext;
        this.className = className;
    }

    public ClassHeader getClassHeader() {
        return classHeader;
    }

    /**
     * Returns the mapping of field headers to field nodes for this solution class.
     *
     * @return the field header => field node mapping
     */
    public Map<FieldHeader, FieldNode> getFields() {
        return fields;
    }

    /**
     * Returns the mapping of method headers to method nodes for this solution class.
     *
     * @return the method header => method node mapping
     */
    public Map<MethodHeader, MethodNode> getMethods() {
        return methods;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classHeader = new ClassHeader(access, name, signature, superName, interfaces);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldHeader fieldHeader = new FieldHeader(className, TransformationUtils.transformAccess(access), name, descriptor, signature);
        FieldNode fieldNode = (FieldNode) super.visitField(TransformationUtils.transformAccess(access), name, descriptor, signature, value);
        fields.put(fieldHeader, fieldNode);
        return fieldNode;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (TransformationUtils.isLambdaMethod(access, name)) {
            name += "$solution";
        }
        MethodNode methodNode = getMethodNode(access, name, descriptor, signature, exceptions);
        methods.put(new MethodHeader(className, TransformationUtils.transformAccess(access), name, descriptor, signature, exceptions), methodNode);
        return methodNode;
    }

    /**
     * Constructs a new method node with the given information.
     * The returned method node ensures that lambda methods of the solution class don't interfere
     * with the ones defined in the submission class.
     *
     * @param access     the method's modifiers
     * @param name       the method's name
     * @param descriptor the method's descriptor
     * @param signature  the method's signature
     * @param exceptions the method's declared exceptions
     * @return a new {@link MethodNode}
     */
    private MethodNode getMethodNode(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodNode methodNode = new MethodNode(ASM9, TransformationUtils.transformAccess(access), name, descriptor, signature, exceptions) {
            @Override
            public void visitMethodInsn(int opcodeAndSource, String owner, String name, String descriptor, boolean isInterface) {
                MethodHeader methodHeader = new MethodHeader(owner, name, descriptor);
                if (transformationContext.methodHasReplacement(methodHeader)) {
                    MethodHeader replacementMethodHeader = transformationContext.getMethodReplacement(methodHeader);
                    super.visitMethodInsn(INVOKESTATIC,
                        replacementMethodHeader.owner(),
                        replacementMethodHeader.name(),
                        replacementMethodHeader.descriptor(),
                        false);
                } else {
                    super.visitMethodInsn(opcodeAndSource,
                        owner,
                        name + (name.startsWith("lambda$") ? "$solution" : ""),
                        descriptor,
                        isInterface);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, Arrays.stream(bootstrapMethodArguments)
                    .map(o -> {
                        if (o instanceof Handle handle && handle.getName().startsWith("lambda$")) {
                            return new Handle(handle.getTag(),
                                handle.getOwner(),
                                handle.getName() + "$solution",
                                handle.getDesc(),
                                handle.isInterface());
                        } else {
                            return o;
                        }
                    })
                    .toArray());
            }
        };

        super.methods.add(methodNode);
        return methodNode;
    }
}
