package com.navercorp.pinpoint.bootstrap.instrument.automation;

import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public enum ClassRepository
    implements Repository<ClassRepository.ClassMirror, ClassRepository.ClassId> {
  INSTANCE;

  private final Logger logger = LoggerFactory.getLogger(getClass());
  protected Map<ClassId, ClassMirror> map = new ConcurrentHashMap<ClassId, ClassMirror>();

  public Optional<ClassMirror> findOne(ClassId id) {
    return Optional.ofNullable(map.get(id));
  }

  public void add(ClassId id, ClassMirror t) {
    logger.debug("put {} into Class Repository.", id);
    map.put(id, t);
  }

  public void delete(ClassId id) {
    map.remove(id);
  }

  public List<ClassMirror> findAll() {
    return new ArrayList<ClassMirror>(map.values());
  }

  @EqualsAndHashCode
  public static class ClassId {
    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private String name;
    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private ClassLoader loader;

    private static final Logger logger = LoggerFactory.getLogger(ClassId.class);

    public static ClassId of(String name, ClassLoader loader) {
      ClassId ret = new ClassId();
      ret.name = name;
      ret.loader = loader;
      logger.debug("create ClassId for: " + name);
      return ret;
    }

    @Override
    public String toString() {
      return name + "@" + loader;
    }
  }

  @EqualsAndHashCode
  public static class ClassMirror {
    private final ClassId classId;
    @Getter
    private Map<Method, AnalysisState> methodStates;
    @Getter
    private List<InstrumentClass> subClasses;
    @Getter
    private List<InstrumentClass> implClasses;
    private static final Logger logger = LoggerFactory.getLogger(ClassMirror.class);
    /**
     * keep track with the current defined class
     */
    @Getter@Setter
    private byte[] classFileBuffer;
    
    public ClassMirror(ClassId classId) {
      methodStates = new HashMap<Method, AnalysisState>();
      subClasses = new ArrayList<InstrumentClass>();
      implClasses = new ArrayList<InstrumentClass>();
      this.classId = classId;
      logger.debug("create ClassMirror for{}", classId.getName());
    }
    
    public void addMethod(Method method, AnalysisState state) {
      methodStates.put(method, state);
    }
    
    public AnalysisState getMethodState(Method method) {
      return methodStates.get(method);
    }
    
    public boolean contains(Method method) {
      return methodStates.containsKey(method);
    }
    public void scanMethod(Method method) {
      if(!methodStates.containsKey(method)) {
        throw new IllegalStateException("ClassMirror scan a non-exist method" + method.getName());
      }
      methodStates.put(method, AnalysisState.Scanned);
    }

    public void addSubClass(InstrumentClass subClass) {
      subClasses.add(subClass);
    }

    public void addImplClass(InstrumentClass implClass) {
      implClasses.add(implClass);
    }

    @Override
    public String toString() {
      return classId.getName() + "," + methodStates;
    }
  }

  public enum AnalysisState {
    Unreachable, Static, Special, Virtual, Interface, Scanned
  }

  @ToString
  @EqualsAndHashCode
  public static class Method {
    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private String name;
    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private String signature;
    @Getter
    @Setter(lombok.AccessLevel.PROTECTED)
    private String owner;

    private Method() {}

    public static Method of(String name, String signature, String owner) {
      Method ret = new Method();
      ret.setName(name);
      ret.setSignature(signature);
      ret.setOwner(owner);
      return ret;
    }
  }

}
