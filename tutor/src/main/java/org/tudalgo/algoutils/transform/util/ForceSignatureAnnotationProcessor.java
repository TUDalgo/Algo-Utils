package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.*;
import org.tudalgo.algoutils.student.annotation.ForceSignature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A processor for the {@link ForceSignature} annotation.
 * An instance of this class processes and holds information on a single class and its members.
 * @author Daniel Mangold
 */
public class ForceSignatureAnnotationProcessor extends ClassVisitor {

    private final ForceSignatureAnnotationVisitor annotationVisitor = new ForceSignatureAnnotationVisitor();
    private final List<FieldLevelVisitor> fieldLevelVisitors = new ArrayList<>();
    private final List<MethodLevelVisitor> methodLevelVisitors = new ArrayList<>();
    private String className;

    private String forcedClassName;
    private final Map<FieldHeader, FieldHeader> forcedFieldsMapping = new HashMap<>();
    private final Map<MethodHeader, MethodHeader> forcedMethodsMapping = new HashMap<>();

    /**
     * Constructs a new {@link ForceSignatureAnnotationProcessor} instance.
     */
    public ForceSignatureAnnotationProcessor() {
        super(Opcodes.ASM9);
    }

    /**
     * Whether the class identifier / name is forced.
     *
     * @return true, if forced, otherwise false
     */
    public boolean classIdentifierIsForced() {
        return forcedClassName != null;
    }

    /**
     * Returns the forced class identifier.
     *
     * @return the forced class identifier
     */
    public String forcedClassIdentifier() {
        return forcedClassName.replace('.', '/');
    }

    /**
     * Whether the given field is forced.
     *
     * @param name the original identifier / name of the field
     * @return true, if forced, otherwise false
     */
    public boolean fieldIdentifierIsForced(String name) {
        return forcedFieldHeader(name) != null;
    }

    /**
     * Returns the field header for a forced field.
     *
     * @param name the original identifier / name of the field
     * @return the field header
     */
    public FieldHeader forcedFieldHeader(String name) {
        return forcedFieldsMapping.entrySet()
            .stream()
            .filter(entry -> name.equals(entry.getKey().name()))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    /**
     * Whether the given method is forced.
     *
     * @param name       the original identifier / name of the method
     * @param descriptor the original descriptor of the method
     * @return true, if forced, otherwise false
     */
    public boolean methodSignatureIsForced(String name, String descriptor) {
        return forcedMethodHeader(name, descriptor) != null;
    }

    /**
     * Returns the method header for a forced method.
     *
     * @param name       the original identifier / name of the method
     * @param descriptor the original descriptor of the method
     * @return the method header
     */
    public MethodHeader forcedMethodHeader(String name, String descriptor) {
        return forcedMethodsMapping.entrySet()
            .stream()
            .filter(entry -> name.equals(entry.getKey().name()) && descriptor.equals(entry.getKey().descriptor()))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals(Constants.FORCE_SIGNATURE_TYPE.getDescriptor())) {
            return annotationVisitor;
        } else {
            return null;
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldLevelVisitor fieldLevelVisitor = new FieldLevelVisitor(className, access, name, descriptor, signature);
        fieldLevelVisitors.add(fieldLevelVisitor);
        return fieldLevelVisitor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodLevelVisitor methodLevelVisitor = new MethodLevelVisitor(className, access, name, descriptor, signature, exceptions);
        methodLevelVisitors.add(methodLevelVisitor);
        return methodLevelVisitor;
    }

    @Override
    public void visitEnd() {
        forcedClassName = annotationVisitor.identifier;

        for (FieldLevelVisitor fieldLevelVisitor : fieldLevelVisitors) {
            ForceSignatureAnnotationVisitor annotationVisitor = fieldLevelVisitor.annotationVisitor;
            if (annotationVisitor == null) continue;
            forcedFieldsMapping.put(
                new FieldHeader(fieldLevelVisitor.owner,
                    fieldLevelVisitor.access,
                    fieldLevelVisitor.name,
                    fieldLevelVisitor.descriptor,
                    fieldLevelVisitor.signature),
                new FieldHeader(fieldLevelVisitor.owner,
                    fieldLevelVisitor.access,
                    annotationVisitor.identifier,
                    fieldLevelVisitor.descriptor,
                    fieldLevelVisitor.signature)
            );
        }

        for (MethodLevelVisitor methodLevelVisitor : methodLevelVisitors) {
            ForceSignatureAnnotationVisitor annotationVisitor = methodLevelVisitor.annotationVisitor;
            if (annotationVisitor == null) continue;
            forcedMethodsMapping.put(
                new MethodHeader(methodLevelVisitor.owner,
                    methodLevelVisitor.access,
                    methodLevelVisitor.name,
                    methodLevelVisitor.descriptor,
                    methodLevelVisitor.signature,
                    methodLevelVisitor.exceptions),
                new MethodHeader(methodLevelVisitor.owner,
                    methodLevelVisitor.access,
                    annotationVisitor.identifier,
                    annotationVisitor.descriptor,
                    methodLevelVisitor.signature,
                    methodLevelVisitor.exceptions)
            );
        }
    }

    /**
     * A field visitor for processing field-level annotations.
     */
    private static class FieldLevelVisitor extends FieldVisitor {

        private final String owner;
        private final int access;
        private final String name;
        private final String descriptor;
        private final String signature;
        private ForceSignatureAnnotationVisitor annotationVisitor;

        private FieldLevelVisitor(String owner, int access, String name, String descriptor, String signature) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(Constants.FORCE_SIGNATURE_TYPE.getDescriptor())) {
                return annotationVisitor = new ForceSignatureAnnotationVisitor();
            } else {
                return null;
            }
        }
    }

    /**
     * A method visitor for processing method-level annotations.
     */
    private static class MethodLevelVisitor extends MethodVisitor {

        private final String owner;
        private final int access;
        private final String name;
        private final String descriptor;
        private final String signature;
        private final String[] exceptions;
        private ForceSignatureAnnotationVisitor annotationVisitor;

        private MethodLevelVisitor(String owner, int access, String name, String descriptor, String signature, String[] exceptions) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(Constants.FORCE_SIGNATURE_TYPE.getDescriptor())) {
                return annotationVisitor = new ForceSignatureAnnotationVisitor();
            } else {
                return null;
            }
        }
    }

    /**
     * An annotation visitor for processing the actual {@link ForceSignature} annotation.
     */
    private static class ForceSignatureAnnotationVisitor extends AnnotationVisitor {

        private String identifier;
        private String descriptor;
        private Type returnType;
        private final List<Type> parameterTypes = new ArrayList<>();

        ForceSignatureAnnotationVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "identifier" -> identifier = (String) value;
                case "descriptor" -> descriptor = (String) value;
                case "returnType" -> returnType = (Type) value;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if (name.equals("parameterTypes")) {
                return new ParameterTypesVisitor();
            } else {
                return null;
            }
        }

        @Override
        public void visitEnd() {
            if ((descriptor == null || descriptor.isEmpty()) && returnType != null) {
                descriptor = Type.getMethodDescriptor(returnType, parameterTypes.toArray(Type[]::new));
            }
        }

        /**
         * A specialized annotation visitor for visiting the values of {@link ForceSignature#parameterTypes()}.
         */
        private class ParameterTypesVisitor extends AnnotationVisitor {

            private ParameterTypesVisitor() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visit(String name, Object value) {
                parameterTypes.add((Type) value);
            }
        }
    }
}
