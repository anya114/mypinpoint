/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.navercorp.pinpoint.profiler.instrument;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.instrument.*;
import com.navercorp.pinpoint.bootstrap.instrument.automation.ClassRepository;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.annotation.Scope;
import com.navercorp.pinpoint.bootstrap.interceptor.registry.InterceptorRegistry;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScope;
import com.navercorp.pinpoint.common.util.Asserts;
import com.navercorp.pinpoint.profiler.ClassFileFilter;
import com.navercorp.pinpoint.profiler.UnmodifiableClassFilter;
import com.navercorp.pinpoint.profiler.context.DefaultMethodDescriptor;
import com.navercorp.pinpoint.profiler.instrument.interceptor.*;
import com.navercorp.pinpoint.profiler.interceptor.factory.AnnotatedInterceptorFactory;
import com.navercorp.pinpoint.profiler.interceptor.registry.InterceptorRegistryBinder;
import com.navercorp.pinpoint.profiler.plugin.DefaultProfilerPluginContext;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import javassist.*;
import javassist.bytecode.*;
import javassist.compiler.CompileError;
import javassist.compiler.Javac;
import javassist.expr.Expr;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Optional;

public class JavassistMethod implements InstrumentMethod {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final boolean isDebug = logger.isDebugEnabled();

  private final InstrumentContext pluginContext;
  private final InterceptorRegistryBinder interceptorRegistryBinder;

  @Getter private final CtBehavior behavior;
  private final InstrumentClass declaringClass;
  private final MethodDescriptor descriptor;

  // TODO fix inject InterceptorDefinitionFactory
  private static final InterceptorDefinitionFactory interceptorDefinitionFactory =
      new InterceptorDefinitionFactory();

  private static final UnmodifiableClassFilter unmodifiableClassFilter =
      new UnmodifiableClassFilter();
  private static final ClassRepository classRepository = ClassRepository.INSTANCE;

  public JavassistMethod(InstrumentContext pluginContext,
      InterceptorRegistryBinder interceptorRegistryBinder, InstrumentClass declaringClass,
      CtBehavior behavior) {
    this.pluginContext = pluginContext;
    this.interceptorRegistryBinder = interceptorRegistryBinder;
    this.behavior = behavior;
    this.declaringClass = declaringClass;

    String[] parameterVariableNames = JavaAssistUtils.getParameterVariableName(behavior);
    int lineNumber = JavaAssistUtils.getLineNumber(behavior);

    DefaultMethodDescriptor descriptor =
        new DefaultMethodDescriptor(behavior.getDeclaringClass().getName(), behavior.getName(),
            getParameterTypes(), parameterVariableNames);
    descriptor.setLineNumber(lineNumber);

    this.descriptor = descriptor;
  }

  @Override public String getName() {
    return behavior.getName();
  }

  @Override public String[] getParameterTypes() {
    return JavaAssistUtils.parseParameterSignature(behavior.getSignature());
  }

  @Override public String getReturnType() {
    if (behavior instanceof CtMethod) {
      try {
        return ((CtMethod) behavior).getReturnType().getName();
      } catch (NotFoundException e) {
        return null;
      }
    }

    return null;
  }

  @Override public int getModifiers() {
    return behavior.getModifiers();
  }

  @Override public boolean isConstructor() {
    return behavior instanceof CtConstructor;
  }

  @Override public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  @Override public int addInterceptor(String interceptorClassName) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    return addInterceptor0(interceptorClassName, null, null, null);
  }

  @Override public int addInterceptor(String interceptorClassName, Object[] constructorArgs)
      throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(constructorArgs, "constructorArgs");
    return addInterceptor0(interceptorClassName, constructorArgs, null, null);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, String scopeName)
      throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(scopeName, "scopeName");
    final InterceptorScope interceptorScope = this.pluginContext.getInterceptorScope(scopeName);
    return addInterceptor0(interceptorClassName, null, interceptorScope, null);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, InterceptorScope scope)
      throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(scope, "scope");
    return addInterceptor0(interceptorClassName, null, scope, null);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, String scopeName,
      ExecutionPolicy executionPolicy) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(scopeName, "scopeName");
    Asserts.notNull(executionPolicy, "executionPolicy");
    final InterceptorScope interceptorScope = this.pluginContext.getInterceptorScope(scopeName);
    return addInterceptor0(interceptorClassName, null, interceptorScope, executionPolicy);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, InterceptorScope scope,
      ExecutionPolicy executionPolicy) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(scope, "scope");
    Asserts.notNull(executionPolicy, "executionPolicy");
    return addInterceptor0(interceptorClassName, null, scope, executionPolicy);
  }


  @Override public int addScopedInterceptor(String interceptorClassName, Object[] constructorArgs,
      String scopeName) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(constructorArgs, "constructorArgs");
    Asserts.notNull(scopeName, "scopeName");
    final InterceptorScope interceptorScope = this.pluginContext.getInterceptorScope(scopeName);
    return addInterceptor0(interceptorClassName, constructorArgs, interceptorScope, null);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, Object[] constructorArgs,
      InterceptorScope scope) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(constructorArgs, "constructorArgs");
    Asserts.notNull(scope, "scope");
    return addInterceptor0(interceptorClassName, constructorArgs, scope, null);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, Object[] constructorArgs,
      String scopeName, ExecutionPolicy executionPolicy) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(constructorArgs, "constructorArgs");
    Asserts.notNull(scopeName, "scopeName");
    Asserts.notNull(executionPolicy, "executionPolicy");
    final InterceptorScope interceptorScope = this.pluginContext.getInterceptorScope(scopeName);
    return addInterceptor0(interceptorClassName, constructorArgs, interceptorScope,
        executionPolicy);
  }

  @Override public int addScopedInterceptor(String interceptorClassName, Object[] constructorArgs,
      InterceptorScope scope, ExecutionPolicy executionPolicy) throws InstrumentException {
    Asserts.notNull(interceptorClassName, "interceptorClassName");
    Asserts.notNull(constructorArgs, "constructorArgs");
    Asserts.notNull(scope, "scope");
    Asserts.notNull(executionPolicy, "executionPolicy");
    return addInterceptor0(interceptorClassName, constructorArgs, scope, executionPolicy);
  }

  @Override public void addInterceptor(int interceptorId) throws InstrumentException {
    Interceptor interceptor = InterceptorRegistry.getInterceptor(interceptorId);

    try {
      addInterceptor0(interceptor, interceptorId);
    } catch (Exception e) {
      throw new InstrumentException(
          "Failed to add interceptor " + interceptor.getClass().getName() + " to " + behavior
              .getLongName(), e);
    }
  }

  private ScopeInfo resolveScopeInfo(String interceptorClassName, InterceptorScope scope,
      ExecutionPolicy policy) {
    final Class<? extends Interceptor> interceptorType =
        pluginContext.injectClass(declaringClass.getClassLoader(), interceptorClassName);

    if (scope == null) {
      Scope interceptorScope = interceptorType.getAnnotation(Scope.class);

      if (interceptorScope != null) {
        String scopeName = interceptorScope.value();
        scope = pluginContext.getInterceptorScope(scopeName);
        policy = interceptorScope.executionPolicy();
      }
    }

    if (scope == null) {
      policy = null;
    } else if (policy == null) {
      policy = ExecutionPolicy.BOUNDARY;
    }

    return new ScopeInfo(scope, policy);
  }

  private static class ScopeInfo {
    private final InterceptorScope scope;
    private final ExecutionPolicy policy;

    public ScopeInfo(InterceptorScope scope, ExecutionPolicy policy) {
      this.scope = scope;
      this.policy = policy;
    }

    public InterceptorScope getScope() {
      return scope;
    }

    public ExecutionPolicy getPolicy() {
      return policy;
    }
  }

  // for internal api
  int addInterceptorInternal(String interceptorClassName, Object[] constructorArgs,
      InterceptorScope scope, ExecutionPolicy executionPolicy) throws InstrumentException {
    if (interceptorClassName == null) {
      throw new NullPointerException("interceptorClassName must not be null");
    }
    return addInterceptor0(interceptorClassName, constructorArgs, scope, executionPolicy);
  }

  private int addInterceptor0(String interceptorClassName, Object[] constructorArgs,
      InterceptorScope scope, ExecutionPolicy executionPolicy) throws InstrumentException {
    try {
      ScopeInfo scopeInfo = resolveScopeInfo(interceptorClassName, scope, executionPolicy);
      Interceptor interceptor = createInterceptor(interceptorClassName, scopeInfo, constructorArgs);
      int interceptorId =
          interceptorRegistryBinder.getInterceptorRegistryAdaptor().addInterceptor(interceptor);

      addInterceptor0(interceptor, interceptorId);
      return interceptorId;
    } catch (CannotCompileException ccex) {
      throw new InstrumentException(
          "Failed to add interceptor " + interceptorClassName + " to " + behavior.getLongName(),
          ccex);
    } catch (NotFoundException nex) {
      throw new InstrumentException(
          "Failed to add interceptor " + interceptorClassName + " to " + behavior.getLongName(),
          nex);
    }
  }

  private Interceptor createInterceptor(String interceptorClassName, ScopeInfo scopeInfo,
      Object[] constructorArgs) {
    ClassLoader classLoader = declaringClass.getClassLoader();

    AnnotatedInterceptorFactory factory = new AnnotatedInterceptorFactory(pluginContext);
    Interceptor interceptor = factory
        .getInterceptor(classLoader, interceptorClassName, constructorArgs, scopeInfo.getScope(),
            scopeInfo.getPolicy(), declaringClass, this);

    return interceptor;
  }

  private void addInterceptor0(Interceptor interceptor, int interceptorId)
      throws CannotCompileException, NotFoundException {
    if (interceptor == null) {
      throw new NullPointerException("interceptor must not be null");
    }

    final InterceptorDefinition interceptorDefinition =
        interceptorDefinitionFactory.createInterceptorDefinition(interceptor.getClass());

    final String localVariableName = initializeLocalVariable(interceptorId);
    int originalCodeOffset = insertBefore(-1, localVariableName);

    boolean localVarsInitialized = false;

    final int offset =
        addBeforeInterceptor(interceptorDefinition, interceptorId, originalCodeOffset);
    if (offset != -1) {
      localVarsInitialized = true;
      originalCodeOffset = offset;
    }

    addAfterInterceptor(interceptorDefinition, interceptorId, localVarsInitialized,
        originalCodeOffset);
  }

  private String initializeLocalVariable(int interceptorId)
      throws CannotCompileException, NotFoundException {

    final String interceptorInstanceVar = InvokeCodeGenerator.getInterceptorVar(interceptorId);
    addLocalVariable(interceptorInstanceVar, Interceptor.class);

    final StringBuilder initVars = new StringBuilder();
    initVars.append(interceptorInstanceVar);
    initVars.append(" = null;");
    return initVars.toString();
  }

  private void addAfterInterceptor(InterceptorDefinition interceptorDefinition, int interceptorId,
      boolean localVarsInitialized, int originalCodeOffset)
      throws NotFoundException, CannotCompileException {

    final Class<?> interceptorClass = interceptorDefinition.getInterceptorClass();
    final CaptureType captureType = interceptorDefinition.getCaptureType();
    if (!isAfterInterceptor(captureType)) {
      return;
    }
    final Method interceptorMethod = interceptorDefinition.getAfterMethod();

    if (interceptorMethod == null) {
      if (isDebug) {
        logger.debug(
            "Skip adding after interceptor because the interceptor doesn't have after method: {}",
            interceptorClass.getName());
      }
      return;
    }

    InvokeAfterCodeGenerator catchGenerator =
        new InvokeAfterCodeGenerator(interceptorId, interceptorDefinition, declaringClass, this,
            pluginContext.getTraceContext(), localVarsInitialized, true);
    String catchCode = catchGenerator.generate();

    if (isDebug) {
      logger.debug("addAfterInterceptor catch behavior:{} code:{}", behavior.getLongName(),
          catchCode);
    }

    CtClass throwable = behavior.getDeclaringClass().getClassPool().get("java.lang.Throwable");
    insertCatch(originalCodeOffset, catchCode, throwable, "$e");


    InvokeAfterCodeGenerator afterGenerator =
        new InvokeAfterCodeGenerator(interceptorId, interceptorDefinition, declaringClass, this,
            pluginContext.getTraceContext(), localVarsInitialized, false);
    final String afterCode = afterGenerator.generate();

    if (isDebug) {
      logger.debug("addAfterInterceptor after behavior:{} code:{}", behavior.getLongName(),
          afterCode);
    }

    behavior.insertAfter(afterCode);
  }

  private boolean isAfterInterceptor(CaptureType captureType) {
    return CaptureType.AFTER == captureType || CaptureType.AROUND == captureType;
  }

  private int addBeforeInterceptor(InterceptorDefinition interceptorDefinition, int interceptorId,
      int pos) throws CannotCompileException, NotFoundException {
    final Class<?> interceptorClass = interceptorDefinition.getInterceptorClass();
    final CaptureType captureType = interceptorDefinition.getCaptureType();
    if (!isBeforeInterceptor(captureType)) {
      return -1;
    }
    final Method interceptorMethod = interceptorDefinition.getBeforeMethod();

    if (interceptorMethod == null) {
      if (isDebug) {
        logger.debug(
            "Skip adding before interceptorDefinition because the interceptorDefinition doesn't have before method: {}",
            interceptorClass.getName());
      }
      return -1;
    }

    final InvokeBeforeCodeGenerator generator =
        new InvokeBeforeCodeGenerator(interceptorId, interceptorDefinition, declaringClass, this,
            pluginContext.getTraceContext());
    final String beforeCode = generator.generate();

    if (isDebug) {
      logger.debug("addBeforeInterceptor before behavior:{} code:{}", behavior.getLongName(),
          beforeCode);
    }

    return insertBefore(pos, beforeCode);
  }

  private boolean isBeforeInterceptor(CaptureType captureType) {
    return CaptureType.BEFORE == captureType || CaptureType.AROUND == captureType;
  }

  private void addLocalVariable(String name, Class<?> type)
      throws CannotCompileException, NotFoundException {
    final String interceptorClassName = type.getName();
    final CtClass interceptorCtClass =
        behavior.getDeclaringClass().getClassPool().get(interceptorClassName);
    behavior.addLocalVariable(name, interceptorCtClass);
  }

  private int insertBefore(int pos, String src) throws CannotCompileException {
    if (isConstructor()) {
      return insertBeforeConstructor(pos, src);
    } else {
      return insertBeforeMethod(pos, src);
    }
  }

  private int insertBeforeMethod(int pos, String src) throws CannotCompileException {
    CtClass cc = behavior.getDeclaringClass();
    CodeAttribute ca = behavior.getMethodInfo().getCodeAttribute();
    if (ca == null)
      throw new CannotCompileException("no method body");

    CodeIterator iterator = ca.iterator();
    Javac jv = new Javac(cc);
    try {
      int nvars = jv.recordParams(behavior.getParameterTypes(), Modifier.isStatic(getModifiers()));
      jv.recordParamNames(ca, nvars);
      jv.recordLocalVariables(ca, 0);
      jv.recordType(getReturnType0());
      jv.compileStmnt(src);
      Bytecode b = jv.getBytecode();
      int stack = b.getMaxStack();
      int locals = b.getMaxLocals();

      if (stack > ca.getMaxStack())
        ca.setMaxStack(stack);

      if (locals > ca.getMaxLocals())
        ca.setMaxLocals(locals);

      if (pos != -1) {
        iterator.insertEx(pos, b.get());
      } else {
        pos = iterator.insertEx(b.get());
      }

      iterator.insert(b.getExceptionTable(), pos);
      behavior.getMethodInfo().rebuildStackMapIf6(cc.getClassPool(), cc.getClassFile2());

      return pos + b.length();
    } catch (NotFoundException e) {
      throw new CannotCompileException(e);
    } catch (CompileError e) {
      throw new CannotCompileException(e);
    } catch (BadBytecode e) {
      throw new CannotCompileException(e);
    }
  }

  private int insertBeforeConstructor(int pos, String src) throws CannotCompileException {
    CtClass cc = behavior.getDeclaringClass();

    CodeAttribute ca = behavior.getMethodInfo().getCodeAttribute();
    CodeIterator iterator = ca.iterator();
    Bytecode b =
        new Bytecode(behavior.getMethodInfo().getConstPool(), ca.getMaxStack(), ca.getMaxLocals());
    b.setStackDepth(ca.getMaxStack());
    Javac jv = new Javac(b, cc);
    try {
      jv.recordParams(behavior.getParameterTypes(), false);
      jv.recordLocalVariables(ca, 0);
      jv.compileStmnt(src);
      ca.setMaxStack(b.getMaxStack());
      ca.setMaxLocals(b.getMaxLocals());
      iterator.skipConstructor();
      if (pos != -1) {
        iterator.insertEx(pos, b.get());
      } else {
        pos = iterator.insertEx(b.get());
      }
      iterator.insert(b.getExceptionTable(), pos);
      behavior.getMethodInfo().rebuildStackMapIf6(cc.getClassPool(), cc.getClassFile2());

      return pos + b.length();
    } catch (NotFoundException e) {
      throw new CannotCompileException(e);
    } catch (CompileError e) {
      throw new CannotCompileException(e);
    } catch (BadBytecode e) {
      throw new CannotCompileException(e);
    }
  }


  private void insertCatch(int from, String src, CtClass exceptionType, String exceptionName)
      throws CannotCompileException {
    CtClass cc = behavior.getDeclaringClass();
    ConstPool cp = behavior.getMethodInfo().getConstPool();
    CodeAttribute ca = behavior.getMethodInfo().getCodeAttribute();
    CodeIterator iterator = ca.iterator();
    Bytecode b = new Bytecode(cp, ca.getMaxStack(), ca.getMaxLocals());
    b.setStackDepth(1);
    Javac jv = new Javac(b, cc);
    try {
      jv.recordParams(behavior.getParameterTypes(), Modifier.isStatic(getModifiers()));
      jv.recordLocalVariables(ca, from);
      int var = jv.recordVariable(exceptionType, exceptionName);
      b.addAstore(var);
      jv.compileStmnt(src);

      int stack = b.getMaxStack();
      int locals = b.getMaxLocals();

      if (stack > ca.getMaxStack())
        ca.setMaxStack(stack);

      if (locals > ca.getMaxLocals())
        ca.setMaxLocals(locals);

      int len = iterator.getCodeLength();
      int pos = iterator.append(b.get());

      ca.getExceptionTable().add(from, len, len, cp.addClassInfo(exceptionType));
      iterator.append(b.getExceptionTable(), pos);
      behavior.getMethodInfo().rebuildStackMapIf6(cc.getClassPool(), cc.getClassFile2());
    } catch (NotFoundException e) {
      throw new CannotCompileException(e);
    } catch (CompileError e) {
      throw new CannotCompileException(e);
    } catch (BadBytecode e) {
      throw new CannotCompileException(e);
    }
  }

  private CtClass getReturnType0() throws NotFoundException {
    return Descriptor.getReturnType(behavior.getMethodInfo().getDescriptor(),
        behavior.getDeclaringClass().getClassPool());
  }

  public void instrument() {
    String property = System.getProperty("pinpoint.autoinstrument");
    if (property != null && property.equals("false"))
      return;
    ClassRepository.ClassId thisId =
        ClassRepository.ClassId.of(declaringClass.getName(), declaringClass.getClassLoader());
    ClassRepository.ClassMirror classMirror = classRepository.findOne(thisId).get();
    classMirror
        .addMethod(ClassRepository.Method.of(getName(), getSignature(), declaringClass.getName()),
            ClassRepository.AnalysisState.Scanned);
    if (behavior.getDeclaringClass().isFrozen()) {
      behavior.getDeclaringClass().defrost();
    }
    try {
      behavior.instrument(new MethodBodyVisitor());
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  private class MethodBodyVisitor extends ExprEditor {

    private static final String MY_INTERCEPTOR =
        "com.navercorp.pinpoint.bootstrap.plugin.automation.AutomationInterceptor";

    @Override public void edit(final MethodCall methodCall) {
      ClassRepository.ClassId calleeId =
          ClassRepository.ClassId.of(methodCall.getClassName(), declaringClass.getClassLoader());
      String thisName = declaringClass.getName(), calleeClassName = methodCall.getClassName();
      ClassRepository.Method method = ClassRepository.Method
          .of(calleeClassName, methodCall.getSignature(), methodCall.getClassName());
      Optional<ClassRepository.ClassMirror> callee = classRepository.findOne(calleeId);
      if (calleeClassName.contains("com.navercorp.pinpoint"))
        return;
      if (unmodifiableClassFilter
          .accept(null, JavaAssistUtils.javaNameToJvmName(calleeClassName), null, null, null)
          == ClassFileFilter.SKIP) {
        return;
      }
      //what if calleeClass is a interface?
      InstrumentClass target =
          pluginContext.getInstrumentClass(declaringClass.getClassLoader(), calleeClassName, null);
      if (!target.isInterceptable()) {
        TransformCallback callback = addTransformCallback(methodCall);
        addTransformer(calleeClassName, callback);
        if(callee.isPresent()) {
          retransformImplments(callee.get(), callback);
          retransformSubClass(callee.get(), callback);
        }
        return;
      }

      if (callee.isPresent()
          && callee.get().getMethodState(method) == ClassRepository.AnalysisState.Scanned)
        return;
      if (thisName.equals(calleeClassName)) {
        try {
          declaringClass
              .addInterceptor(MethodFilters.name(methodCall.getMethodName()), MY_INTERCEPTOR);
          callee.get().addMethod(method, ClassRepository.AnalysisState.Scanned);
        } catch (InstrumentException e) {
          logger.error("add AutomationInterceptor error in class {}", thisName);
        }
      } else {
        // loaded
        if (callee.isPresent()) {
          ClassRepository.ClassMirror mirror = callee.get();
          try {
            pluginContext
                .retransform(Class.forName(calleeClassName, false, declaringClass.getClassLoader()),
                    addTransformCallback(methodCall));
            mirror.addMethod(method, ClassRepository.AnalysisState.Scanned);
          } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);
          }
        } else {
          // not loaded
          addTransformer(calleeClassName, addTransformCallback(methodCall));
        }
      }
    }

    private void retransformImplments(ClassRepository.ClassMirror target,
        TransformCallback callback) {
      for (InstrumentClass impl : target.getImplClasses()) {
        try {
          pluginContext
              .retransform(Class.forName(impl.getName(), false, declaringClass.getClassLoader()),
                  callback);
        } catch (ClassNotFoundException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    private void retransformSubClass(ClassRepository.ClassMirror target,
        TransformCallback callback) {
      for (InstrumentClass subClass : target.getSubClasses()) {
        try {
          pluginContext.retransform(
              Class.forName(subClass.getName(), false, declaringClass.getClassLoader()), callback);
        } catch (ClassNotFoundException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    private void addTransformer(String className, TransformCallback transformCallback) {
      if (pluginContext instanceof DefaultProfilerPluginContext) {
        DefaultProfilerPluginContext context = (DefaultProfilerPluginContext) pluginContext;
        if (context.getAgent().getClassFileTransformerDispatcher()
            .findTransformer(JavaAssistUtils.javaNameToJvmName(className)) == null) {
          context.getAgent().getClassFileTransformerDispatcher()
              .addTransformer(pluginContext, className, transformCallback);
        }
      }
    }

    private TransformCallback addTransformCallback(final MethodCall methodCall) {
      return new TransformCallback() {
        @Override public byte[] doInTransform(Instrumentor instrumentor, ClassLoader classLoader,
            String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws InstrumentException {
          InstrumentClass targetClass =
              instrumentor.getInstrumentClass(classLoader, className, classfileBuffer);
          if (!targetClass.isInterceptable())
            return null;
          String[] types = null;
          try {
            CtClass[] parameterTypes = methodCall.getMethod().getParameterTypes();
            types = new String[parameterTypes.length];
            for (int i = 0; i < types.length; i++) {
              types[i] = parameterTypes[i].getName();
            }
          } catch (NotFoundException e) {
          }
          InstrumentMethod targetMethod =
              targetClass.getDeclaredMethod(methodCall.getMethodName(), types);
          if (targetMethod != null) {
            targetMethod.addInterceptor(MY_INTERCEPTOR);
          }
          return targetClass.toBytecode();
        }
      };
    }

    private boolean isInvokeStatic(MethodCall methodCall) {
      return Opcode.INVOKESTATIC == getOp(methodCall);
    }

    private int getOp(MethodCall mc) {
      try {
        Field pos = Expr.class.getDeclaredField("currentPos");
        pos.setAccessible(true);
        Field iterator = Expr.class.getDeclaredField("iterator");
        iterator.setAccessible(true);
        return ((CodeIterator) iterator.get(mc)).byteAt(pos.getInt(mc));
      } catch (Exception e) {
      }
      return -1;
    }

    private boolean isInvokeSpecial(MethodCall mc) {
      return Opcode.INVOKESPECIAL == getOp(mc);
    }

    private boolean isInvokeVirtual(MethodCall mc) {
      return Opcode.INVOKEVIRTUAL == getOp(mc);
    }

    private boolean isInvokeInterface(MethodCall methodCall) {
      return Opcode.INVOKEINTERFACE == getOp(methodCall);
    }
  }

  public String getSignature() {
    return behavior.getSignature();
  }
}
