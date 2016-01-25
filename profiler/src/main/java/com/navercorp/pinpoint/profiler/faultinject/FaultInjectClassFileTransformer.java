package com.navercorp.pinpoint.profiler.faultinject;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.faultinject.FaultInjector;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClassPool;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentContext;
import com.navercorp.pinpoint.bootstrap.instrument.MethodFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Created by apple on 16/1/17.
 */
public class FaultInjectClassFileTransformer implements ClassFileTransformer {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final InstrumentClassPool instrumentClassPool;
  private final InstrumentContext instrumentContext;
  private List<FaultInjector.FaultType> types;
  private final Map<String, List<MethodFaultPair>> faultMap;


  private static class MethodFaultPair {
    String methodName;
    FaultInjector.FaultType faultType;
  }

  public FaultInjectClassFileTransformer(ProfilerConfig config,
      InstrumentClassPool instrumentClassPool, InstrumentContext instrumentContext) {
    this.instrumentClassPool = instrumentClassPool;
    this.instrumentContext = instrumentContext;
    registerInjectorType();
    faultMap = new HashMap<String, List<MethodFaultPair>>();
    initFaultMap(config);
  }

  private void initFaultMap(ProfilerConfig config) {
    for (String fault : config.getFaultInjectList()) {
      if(fault.isEmpty()) {
        continue;
      }
      String className = toClassName(fault);
      String methodName = toMethodName(fault);
      String faultTypeName = toFaultType(fault);
      faultMap.putIfAbsent(className, new ArrayList<MethodFaultPair>());
      List<MethodFaultPair> methodFaultPairs = faultMap.get(className);
      MethodFaultPair newPair = new MethodFaultPair();
      newPair.methodName = methodName;
      if (faultTypeName == null) {
        newPair.faultType = randomType();
      } else {
        newPair.faultType = FaultInjector.FaultType.valueOf(faultTypeName);
      }
      methodFaultPairs.add(newPair);
    }
  }

  String toClassName(String fault) {
    final int classEndPosition = fault.lastIndexOf(".");
    if (classEndPosition <= 0) {
      throw new IllegalArgumentException(
          "invalid full qualified method name(" + fault + "). not found method");
    }

    return fault.substring(0, classEndPosition);
  }

  String toMethodName(String fault) {
    final int methodBeginPosition = fault.lastIndexOf(".") + 1;
    final int typeBeginPosition = fault.lastIndexOf(":");
    if (methodBeginPosition == 0 || methodBeginPosition == fault.length()) {
      throw new IllegalArgumentException(
          "invalid full qualified method name(" + fault + "). not found method");
    }
    return typeBeginPosition == -1 ?
        fault.substring(methodBeginPosition) :
        fault.substring(methodBeginPosition, typeBeginPosition);
  }

  String toFaultType(String fault) {
    final int typeBeginPosition = fault.lastIndexOf(":");
    return typeBeginPosition == -1 ? null : fault.substring(typeBeginPosition + 1);
  }


  private void registerInjectorType() {
    types = Arrays.asList(FaultInjector.FaultType.values());
  }

  @Override
  public byte[] transform(ClassLoader loader, String jvmClassName, Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain, byte[] classfileBuffer)
      throws IllegalClassFormatException {
    byte[] ret = null;
    String classInternalName = jvmClassName.replace('/', '.');
    if (!faultMap.containsKey(classInternalName))
      return null;
    try {
      if(jvmClassName.contains("SolrSearchServiceImpl")){
        logger.info("fault inject transform {}", jvmClassName);
      }
      InstrumentClass instrumentClass = instrumentClassPool
          .getClass(instrumentContext, loader, classInternalName, classfileBuffer);
      for (MethodFaultPair pair : faultMap.get(classInternalName)) {
        instrumentClass.addFaultInjector(MethodFilters.name(pair.methodName),
           new FaultInjector(pair.faultType));
      }
      ret = instrumentClass.toBytecode();
    } catch (Exception e) {
      logger.debug(e.getMessage(), e);
    }
    return ret;
  }

  private FaultInjector.FaultType randomType() {
    return types.get(new Random().nextInt(types.size()));
  }
}
