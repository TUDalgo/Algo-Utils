package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionExecutionHandler;
import org.tudalgo.algoutils.transform.methods.LambdaMethodVisitor;
import org.tudalgo.algoutils.transform.methods.MissingMethodVisitor;
import org.tudalgo.algoutils.transform.methods.SubmissionMethodVisitor;
import org.tudalgo.algoutils.transform.util.*;
import org.objectweb.asm.*;

import java.util.*;
import java.util.stream.Collectors;

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
public class SubmissionClassVisitor extends ClassVisitor {

    private final TransformationContext transformationContext;
    private final SubmissionClassInfo submissionClassInfo;
    private final ClassHeader originalClassHeader;
    private final ClassHeader computedClassHeader;

    private final Set<FieldHeader> visitedFields = new HashSet<>();
    private final Set<MethodHeader> visitedMethods = new HashSet<>();

    public SubmissionClassVisitor(ClassVisitor classVisitor,
                                  TransformationContext transformationContext,
                                  String submissionClassName) {
        super(ASM9, classVisitor);
        this.transformationContext = transformationContext;
        this.submissionClassInfo = transformationContext.getSubmissionClassInfo(submissionClassName);
        this.originalClassHeader = submissionClassInfo.getOriginalClassHeader();
        this.computedClassHeader = submissionClassInfo.getComputedClassHeader();
    }

    /**
     * Visits the header of the class, replacing it with the solution class' header, if one is present.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        submissionClassInfo.getSolutionClass()
            .map(SolutionClassNode::getClassHeader)
            .orElse(originalClassHeader)
            .visitClass(getDelegate(), version);
    }

    /**
     * Visits a field of the submission class and transforms it if a solution class is present.
     */
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldHeader fieldHeader = submissionClassInfo.getComputedFieldHeader(name);

        if (TransformationUtils.contextIsCompatible(access, fieldHeader.access()) &&
            transformationContext.descriptorIsCompatible(descriptor, fieldHeader.descriptor())) {
            visitedFields.add(fieldHeader);
            return fieldHeader.toFieldVisitor(getDelegate(), value);
        } else {
            return super.visitField(TransformationUtils.transformAccess(access),
                name + "$submission",
                transformationContext.toComputedDescriptor(descriptor),
                signature,
                value);
        }
    }

    /**
     * Visits a method of a submission class and transforms it.
     * Enables invocation logging, substitution and, if a solution class is present, delegation.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (TransformationUtils.isLambdaMethod(access, name)) {
            MethodHeader methodHeader = new MethodHeader(originalClassHeader.name(),
                access,
                name,
                transformationContext.toComputedDescriptor(descriptor),
                signature,
                exceptions);
            return new LambdaMethodVisitor(methodHeader.toMethodVisitor(getDelegate()),
                transformationContext,
                submissionClassInfo,
                methodHeader);
        } else {
            MethodHeader originalMethodHeader = new MethodHeader(originalClassHeader.name(), access, name, descriptor, signature, exceptions);
            MethodHeader computedMethodHeader = submissionClassInfo.getComputedMethodHeader(name, descriptor);

            if (TransformationUtils.contextIsCompatible(access, computedMethodHeader.access()) &&
                transformationContext.descriptorIsCompatible(descriptor, computedMethodHeader.descriptor())) {
                visitedMethods.add(computedMethodHeader);
            } else {
                computedMethodHeader = new MethodHeader(computedMethodHeader.owner(),
                    access,
                    name + "$submission",
                    transformationContext.toComputedDescriptor(descriptor),
                    signature,
                    exceptions);
            }
            return new SubmissionMethodVisitor(computedMethodHeader.toMethodVisitor(getDelegate()),
                transformationContext,
                submissionClassInfo,
                originalMethodHeader,
                computedMethodHeader);
        }
    }

    /**
     * Adds all remaining fields and methods from the solution class that have not already
     * been visited (e.g., lambdas).
     * Injects methods for retrieving the original class, field and method headers during runtime.
     */
    @Override
    public void visitEnd() {
        Optional<SolutionClassNode> solutionClass = submissionClassInfo.getSolutionClass();
        if (solutionClass.isPresent()) {
            // add missing fields
            solutionClass.get()
                .getFields()
                .entrySet()
                .stream()
                .filter(entry -> !visitedFields.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(fieldNode -> fieldNode.accept(getDelegate()));
            // add missing methods (including lambdas)
            solutionClass.get()
                .getMethods()
                .entrySet()
                .stream()
                .filter(entry -> !visitedMethods.contains(entry.getKey()))
                .forEach(entry -> {
                    MethodHeader methodHeader = entry.getKey();
                    MethodNode methodNode = entry.getValue();

                    if (TransformationUtils.isLambdaMethod(methodHeader.access(), methodHeader.name())) {
                        methodNode.accept(getDelegate());
                    } else {
                        MethodVisitor mv = methodHeader.toMethodVisitor(getDelegate());
                        methodNode.accept(new MissingMethodVisitor(mv, transformationContext, submissionClassInfo, methodHeader));
                    }
                });
        }

        injectClassMetadata();
        injectFieldMetadata();
        injectMethodMetadata();

        transformationContext.addVisitedClass(computedClassHeader.name());
        super.visitEnd();
    }

    /**
     * Injects a static method {@code getOriginalClassHeader()} into the submission class.
     * This injected method returns the original class header of the class pre-transformation.
     */
    private void injectClassMetadata() {
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_CLASS_HEADER.toMethodVisitor(getDelegate());

        int maxStack = originalClassHeader.buildHeader(mv);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(maxStack, 0);
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
        int maxStack, stackSize;
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_FIELD_HEADERS.toMethodVisitor(getDelegate());

        mv.visitIntInsn(SIPUSH, fieldHeaders.size());
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
        maxStack = stackSize = 1;
        int i = 0;
        for (FieldHeader fieldHeader : fieldHeaders) {
            mv.visitInsn(DUP);
            maxStack = Math.max(maxStack, ++stackSize);
            mv.visitIntInsn(SIPUSH, i++);
            maxStack = Math.max(maxStack, ++stackSize);
            int stackSizeUsed = fieldHeader.buildHeader(mv);
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
        mv.visitMaxs(maxStack, 0);
    }

    /**
     * Injects a static method {@code getOriginalMethodHeaders()} into the submission class.
     * This injected method returns the set of original method headers of the class pre-transformation.
     */
    private void injectMethodMetadata() {
        Set<MethodHeader> methodHeaders = submissionClassInfo.getOriginalMethodHeaders()
            .stream()
            .filter(methodHeader -> (methodHeader.access() & ACC_SYNTHETIC) == 0)
            .collect(Collectors.toSet());
        int maxStack, stackSize;
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_METHODS_HEADERS.toMethodVisitor(getDelegate());

        mv.visitIntInsn(SIPUSH, methodHeaders.size());
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
        maxStack = stackSize = 1;
        int i = 0;
        for (MethodHeader methodHeader : methodHeaders) {
            mv.visitInsn(DUP);
            maxStack = Math.max(maxStack, ++stackSize);
            mv.visitIntInsn(SIPUSH, i++);
            maxStack = Math.max(maxStack, ++stackSize);
            int stackSizeUsed = methodHeader.buildHeader(mv);
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
        mv.visitMaxs(maxStack, 0);
    }
}
