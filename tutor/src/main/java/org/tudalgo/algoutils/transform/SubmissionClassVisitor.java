package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.stream.Collectors;

import static org.tudalgo.algoutils.transform.util.TransformationUtils.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * A class visitor merging a submission class with its corresponding solution class, should one exist.
 * The heart piece of the {@link SolutionMergingClassTransformer} processing chain.
 * <br>
 * Main features:
 * <ul>
 *     <li>
 *         <b>Method invocation logging</b><br>
 *         Logs the parameter values the method was called with.
 *         This allows the user to verify that a method was called and also that it was called with
 *         the right parameters.
 *         If the target method is not static or a constructor, the object the method was invoked on
 *         is logged as well.
 *     </li>
 *     <li>
 *         <b>Method substitution</b><br>
 *         Allows for "replacement" of a method at runtime.
 *         While the method itself must still be invoked, it will hand over execution to the provided
 *         substitution.
 *         This can be useful when a method should always return a certain value, regardless of object state
 *         or for making a non-deterministic method (e.g., RNG) return deterministic values.
 *         Replacing constructors is currently not supported.
 *         Can be combined with invocation logging.
 *     </li>
 *     <li>
 *         <b>Method delegation</b><br>
 *         Will effectively "replace" the code of the original submission with the one from the solution.
 *         While the instructions from both submission and solution are present in the merged method, only
 *         one can be active at a time.
 *         This allows for improved unit testing by not relying on submission code transitively.
 *         If this mechanism is used and no solution class is associated with this submission class or
 *         the solution class does not contain a matching method, the submission code will be used
 *         as a fallback.
 *         Can be combined with invocation logging.
 *     </li>
 * </ul>
 * All of these options can be enabled / disabled via {@link SubmissionExecutionHandler}.
 *
 * <br><br>
 *
 * Generally, the body of a transformed method would look like this in Java source code:
 * <pre>
 * SubmissionExecutionHandler.Internal submissionExecutionHandler = SubmissionExecutionHandler.getInstance().new Internal();
 * MethodHeader methodHeader = new MethodHeader(...);  // parameters are hardcoded during transformation
 *
 * if (submissionExecutionHandler.logInvocation(methodHeader)) {
 *     submissionExecutionHandler.addInvocation(new Invocation(...)  // new Invocation() if constructor or static method
 *         .addParameter(...)  // for each parameter
 *         ...);
 * }
 * if (submissionExecutionHandler.useSubstitution(methodHeader)) {
 *     MethodSubstitution methodSubstitution = submissionExecutionHandler.getSubstitution(methodHeader);
 *
 *     // if constructor
 *     MethodSubstitution.ConstructorInvocation constructorInvocation = methodSubstitution.getConstructorInvocation();
 *     if (constructorInvocation.owner().equals(<superclass>) && constructorInvocation.descriptor().equals(<descriptor>) {
 *         Object[] args = constructorInvocation.args();
 *         super(args[0], args[1], ...);
 *     }
 *     else if ...  // for every superclass constructor
 *     else if (constructorInvocation.owner().equals(<class>) && constructorInvocation.descriptor().equals(<descriptor>) {
 *         Object[] args = constructorInvocation.args();
 *         this(args[0], args[1], ...);
 *     }
 *     else if ... // for every constructor in submission class
 *     else {
 *         throw new IllegalArgumentException(...);  // if no matching constructor was found
 *     }
 *
 *     return methodSubstitution.execute(new Invocation(...) ...);  // same as above
 * }
 * if (submissionExecutionHandler.useSubmissionImpl(methodHeader)) {
 *     ...  // submission code
 * } else {
 *     ...  // solution code
 * }
 * </pre>
 * If no solution class is associated with the submission class, the submission code is executed unconditionally.
 * <br>
 * Additionally, the following methods are injected into the submission class:
 * <pre>
 * public static ClassHeader getOriginalClassHeader() {...}
 * public static Set&lt;FieldHeader&gt; getOriginalFieldHeaders() {...}
 * public static Set&lt;MethodHeader&gt; getOriginalMethodHeaders() {...}
 * </pre>
 *
 * @see SubmissionMethodVisitor
 * @see SubmissionExecutionHandler
 * @author Daniel Mangold
 */
class SubmissionClassVisitor extends ClassVisitor {

    private final boolean defaultTransformationsOnly;
    private final TransformationContext transformationContext;
    private final String className;
    private final SubmissionClassInfo submissionClassInfo;

    private final Set<FieldHeader> visitedFields = new HashSet<>();
    private final Map<FieldHeader, FieldNode> solutionFieldNodes;

    private final Set<MethodHeader> visitedMethods = new HashSet<>();
    private final Map<MethodHeader, MethodNode> solutionMethodNodes;

    SubmissionClassVisitor(ClassVisitor classVisitor,
                           TransformationContext transformationContext,
                           String submissionClassName) {
        super(ASM9, classVisitor);
        this.transformationContext = transformationContext;
        this.className = transformationContext.getSubmissionClassInfo(submissionClassName).getComputedClassName();
        this.submissionClassInfo = transformationContext.getSubmissionClassInfo(submissionClassName);

        Optional<SolutionClassNode> solutionClass = submissionClassInfo.getSolutionClass();
        if (solutionClass.isPresent()) {
            this.defaultTransformationsOnly = false;
            this.solutionFieldNodes = solutionClass.get().getFields();
            this.solutionMethodNodes = solutionClass.get().getMethods();
        } else {
            System.err.printf("No corresponding solution class found for %s. Only applying default transformations.%n", submissionClassName);
            this.defaultTransformationsOnly = true;
            this.solutionFieldNodes = Collections.emptyMap();
            this.solutionMethodNodes = Collections.emptyMap();
        }
    }

    /**
     * Visits the header of the class, replacing it with the solution class' header, if one is present.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        submissionClassInfo.getSolutionClass()
            .map(SolutionClassNode::getClassHeader)
            .orElse(submissionClassInfo.getOriginalClassHeader())
            .visitClass(getDelegate(), version);
    }

    /**
     * Visits a field of the submission class and transforms it if a solution class is present.
     */
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldHeader fieldHeader = submissionClassInfo.getComputedFieldHeader(name);

        if ((access & ACC_STATIC) == (fieldHeader.access() & ACC_STATIC)) {
            visitedFields.add(fieldHeader);
            return fieldHeader.toFieldVisitor(getDelegate(), value);
        } else {
            return super.visitField(access & ~ACC_FINAL, name + "$submission", fieldHeader.descriptor(), fieldHeader.signature(), value);
        }
    }

    /**
     * Visits a method of a submission class and transforms it.
     * Enables invocation logging, substitution and, if a solution class is present, delegation.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodHeader originalMethodHeader = new MethodHeader(submissionClassInfo.getOriginalClassHeader().name(), access, name, descriptor, signature, exceptions);
        MethodHeader computedMethodHeader = submissionClassInfo.getComputedMethodHeader(name, descriptor);

        // if method is lambda, skip transformation
        if ((access & ACC_SYNTHETIC) != 0 && originalMethodHeader.name().startsWith("lambda$")) {
            return originalMethodHeader.toMethodVisitor(getDelegate());
        } else if ((originalMethodHeader.access() & ACC_STATIC) != (computedMethodHeader.access() & ACC_STATIC)) {
            Type returnType = TransformationUtils.getComputedType(transformationContext, Type.getReturnType(descriptor));
            Type[] parameterTypes = Arrays.stream(Type.getArgumentTypes(descriptor))
                .map(type -> TransformationUtils.getComputedType(transformationContext, type))
                .toArray(Type[]::new);
            MethodHeader methodHeader = new MethodHeader(computedMethodHeader.owner(),
                access,
                name + "$submission",
                Type.getMethodDescriptor(returnType, parameterTypes),
                signature,
                exceptions);
            return new SubmissionMethodVisitor(methodHeader.toMethodVisitor(getDelegate()),
                transformationContext,
                submissionClassInfo,
                originalMethodHeader,
                methodHeader);
        } else {
            visitedMethods.add(computedMethodHeader);
            return new SubmissionMethodVisitor(computedMethodHeader.toMethodVisitor(getDelegate()),
                transformationContext,
                submissionClassInfo,
                originalMethodHeader,
                submissionClassInfo.getComputedMethodHeader(originalMethodHeader.name(), originalMethodHeader.descriptor()));
        }
    }

    /**
     * Adds all remaining fields and methods from the solution class that have not already
     * been visited (e.g., lambdas).
     * Injects methods for retrieving the original class, field and method headers during runtime.
     */
    @Override
    public void visitEnd() {
        if (!defaultTransformationsOnly) {
            // add missing fields
            solutionFieldNodes.entrySet()
                .stream()
                .filter(entry -> !visitedFields.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(fieldNode -> fieldNode.accept(getDelegate()));
            // add missing methods (including lambdas)
            solutionMethodNodes.entrySet()
                .stream()
                .filter(entry -> !visitedMethods.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(methodNode -> methodNode.accept(getDelegate()));
        }

        injectClassMetadata();
        injectFieldMetadata();
        injectMethodMetadata();

        super.visitEnd();
    }

    /**
     * Injects a static method {@code getOriginalClassHeader()} into the submission class.
     * This injected method returns the original class header of the class pre-transformation.
     */
    private void injectClassMetadata() {
        ClassHeader classHeader = submissionClassInfo.getOriginalClassHeader();
        Label startLabel = new Label();
        Label endLabel = new Label();
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_CLASS_HEADER.toMethodVisitor(getDelegate());

        mv.visitLabel(startLabel);
        int maxStack = buildHeader(mv, classHeader);
        mv.visitInsn(ARETURN);
        mv.visitLabel(endLabel);
        mv.visitLocalVariable("this",
            Type.getObjectType(className).getDescriptor(),
            null,
            startLabel,
            endLabel,
            0);
        mv.visitMaxs(maxStack, 1);
    }

    /**
     * Injects a static method {@code getOriginalFieldHeaders()} into the submission class.
     * This injected method returns the set of original field headers of the class pre-transformation.
     */
    private void injectFieldMetadata() {
        Set<FieldHeader> fieldHeaders = submissionClassInfo.getOriginalFieldHeaders()
            .stream()
            .filter(fieldHeader -> (fieldHeader.access() & ACC_SYNTHETIC) == 0)
            .collect(Collectors.toSet());
        Label startLabel = new Label();
        Label endLabel = new Label();
        int maxStack, stackSize;
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_FIELD_HEADERS.toMethodVisitor(getDelegate());

        mv.visitLabel(startLabel);
        mv.visitIntInsn(SIPUSH, fieldHeaders.size());
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
        maxStack = stackSize = 1;
        int i = 0;
        for (FieldHeader fieldHeader : fieldHeaders) {
            mv.visitInsn(DUP);
            maxStack = Math.max(maxStack, ++stackSize);
            mv.visitIntInsn(SIPUSH, i++);
            maxStack = Math.max(maxStack, ++stackSize);
            int stackSizeUsed = buildHeader(mv, fieldHeader);
            maxStack = Math.max(maxStack, stackSize++ + stackSizeUsed);
            mv.visitInsn(AASTORE);
            stackSize -= 3;
        }
        mv.visitMethodInsn(INVOKESTATIC,
            Constants.SET_TYPE.getInternalName(),
            "of",
            Type.getMethodDescriptor(Constants.SET_TYPE, Type.getType(Object[].class)),
            true);
        mv.visitInsn(ARETURN);
        mv.visitLabel(endLabel);
        mv.visitLocalVariable("this",
            Type.getObjectType(className).getDescriptor(),
            null,
            startLabel,
            endLabel,
            0);
        mv.visitMaxs(maxStack, 1);
    }

    /**
     * Injects a static method {@code getOriginalMethodHeaders()} into the submission class.
     * This injected method returns the set of original method headers of the class pre-transformation.
     */
    private void injectMethodMetadata() {
        Set<MethodHeader> methodHeaders = submissionClassInfo.getOriginalMethodHeaders()
            .stream()
            .filter(methodHeader -> (methodHeader.access() & ACC_SYNTHETIC) == 0)
            .collect(Collectors.toSet());;
        Label startLabel = new Label();
        Label endLabel = new Label();
        int maxStack, stackSize;
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_METHODS_HEADERS.toMethodVisitor(getDelegate());

        mv.visitLabel(startLabel);
        mv.visitIntInsn(SIPUSH, methodHeaders.size());
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
        maxStack = stackSize = 1;
        int i = 0;
        for (MethodHeader methodHeader : methodHeaders) {
            mv.visitInsn(DUP);
            maxStack = Math.max(maxStack, ++stackSize);
            mv.visitIntInsn(SIPUSH, i++);
            maxStack = Math.max(maxStack, ++stackSize);
            int stackSizeUsed = buildHeader(mv, methodHeader);
            maxStack = Math.max(maxStack, stackSize++ + stackSizeUsed);
            mv.visitInsn(AASTORE);
            stackSize -= 3;
        }
        mv.visitMethodInsn(INVOKESTATIC,
            Constants.SET_TYPE.getInternalName(),
            "of",
            Type.getMethodDescriptor(Constants.SET_TYPE, Type.getType(Object[].class)),
            true);
        mv.visitInsn(ARETURN);
        mv.visitLabel(endLabel);
        mv.visitLocalVariable("this",
            Type.getObjectType(className).getDescriptor(),
            null,
            startLabel,
            endLabel,
            0);
        mv.visitMaxs(maxStack, 1);
    }
}
