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
public class ForceSignatureAnnotationProcessor {

    /**
     * The forced identifier of the class, if any.
     */
    private String forcedClassIdentifier;

    /**
     * A mapping of the actual field header to the forced one.
     */
    private final Map<FieldHeader, FieldHeader> forcedFieldsMapping = new HashMap<>();

    /**
     * A mapping of the actual method header to the forced one.
     */
    private final Map<MethodHeader, MethodHeader> forcedMethodsMapping = new HashMap<>();

    /**
     * Constructs a new {@link ForceSignatureAnnotationProcessor} instance and processes the
     * {@link ForceSignature} annotation using the given class reader.
     *
     * @param reader the class reader to use for processing
     */
    public ForceSignatureAnnotationProcessor(ClassReader reader) {
        reader.accept(new ClassLevelVisitor(reader.getClassName()), 0);
    }

    /**
     * Whether the class identifier / name is forced.
     *
     * @return true, if forced, otherwise false
     */
    public boolean classIdentifierIsForced() {
        return forcedClassIdentifier != null;
    }

    /**
     * Returns the forced class identifier.
     *
     * @return the forced class identifier
     */
    public String forcedClassIdentifier() {
        return forcedClassIdentifier.replace('.', '/');
    }

    /**
     * Whether the given field is forced.
     *
     * @param identifier the original identifier / name of the field
     * @return true, if forced, otherwise false
     */
    public boolean fieldIdentifierIsForced(String identifier) {
        return forcedFieldHeader(identifier) != null;
    }

    /**
     * Returns the field header for a forced field.
     *
     * @param identifier the original identifier / name of the field
     * @return the field header
     */
    public FieldHeader forcedFieldHeader(String identifier) {
        return forcedFieldsMapping.entrySet()
            .stream()
            .filter(entry -> identifier.equals(entry.getKey().name()))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    /**
     * Whether the given method is forced.
     *
     * @param identifier the original identifier / name of the method
     * @param descriptor the original descriptor of the method
     * @return true, if forced, otherwise false
     */
    public boolean methodSignatureIsForced(String identifier, String descriptor) {
        return forcedMethodHeader(identifier, descriptor) != null;
    }

    /**
     * Returns the method header for a forced method.
     *
     * @param identifier the original identifier / name of the method
     * @param descriptor the original descriptor of the method
     * @return the method header
     */
    public MethodHeader forcedMethodHeader(String identifier, String descriptor) {
        return forcedMethodsMapping.entrySet()
            .stream()
            .filter(entry -> identifier.equals(entry.getKey().name()) && descriptor.equals(entry.getKey().descriptor()))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    /**
     * A visitor for processing class-level annotations.
     */
    private class ClassLevelVisitor extends ClassVisitor {

        private final String name;

        private ForceSignatureAnnotationVisitor annotationVisitor;
        private final List<FieldLevelVisitor> fieldLevelVisitors = new ArrayList<>();
        private final List<MethodLevelVisitor> methodLevelVisitors = new ArrayList<>();

        private ClassLevelVisitor(String name) {
            super(Opcodes.ASM9);
            this.name = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(Constants.FORCE_SIGNATURE_TYPE.getDescriptor())) {
                return annotationVisitor = new ForceSignatureAnnotationVisitor();
            } else {
                return null;
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldLevelVisitor fieldLevelVisitor = new FieldLevelVisitor(this.name, access, name, descriptor, signature);
            fieldLevelVisitors.add(fieldLevelVisitor);
            return fieldLevelVisitor;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodLevelVisitor methodLevelVisitor = new MethodLevelVisitor(this.name, access, name, descriptor, signature, exceptions);
            methodLevelVisitors.add(methodLevelVisitor);
            return methodLevelVisitor;
        }

        @Override
        public void visitEnd() {
            forcedClassIdentifier = annotationVisitor != null ? annotationVisitor.identifier : null;

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
