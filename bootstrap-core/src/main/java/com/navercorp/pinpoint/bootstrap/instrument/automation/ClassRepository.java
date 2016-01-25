package com.navercorp.pinpoint.bootstrap.instrument.automation;

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
    if (!map.containsKey(id))
      map.put(id, t);
  }

  public void delete(ClassId id) {
    map.remove(id);
  }

  public List<ClassMirror> findAll() {
    return new ArrayList<ClassMirror>(map.values());
  }

  @EqualsAndHashCode public static class ClassId {
    @Getter private final String name;
    @Getter private final ClassLoader loader;

    private static final Logger logger = LoggerFactory.getLogger(ClassId.class);

    public static ClassId of(String name, ClassLoader loader) {
      ClassId ret = new ClassId(name, loader);
      logger.debug("create ClassId for: " + name);
      return ret;
    }

    private ClassId(String name, ClassLoader loader) {
      this.name = name;
      this.loader = loader;
    }

    @Override public String toString() {
      return name + "@" + loader;
    }
  }


  @EqualsAndHashCode public static class ClassMirror {
    @Getter private final ClassId classId;
    @Getter private Map<Method, AnalysisState> methodStates;
    @Getter private List<ClassMirror> subClasses;
    @Getter private List<ClassMirror> implClasses;
    private static final Logger logger = LoggerFactory.getLogger(ClassMirror.class);
    /**
     * keep track with the current defined class
     */

    public ClassMirror(ClassId classId) {
      methodStates = new HashMap<Method, AnalysisState>();
      subClasses = new ArrayList<ClassMirror>();
      implClasses = new ArrayList<ClassMirror>();
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

    public void addSubClass(ClassMirror subClass) {
      subClasses.add(subClass);
    }

    public void addImplClass(ClassMirror implClass) {
      implClasses.add(implClass);
    }

    @Override public String toString() {
      return classId.getName() + "," + methodStates;
    }
  }


  public enum AnalysisState {
    Unreachable, Static, Special, Virtual, Interface, Scanned
  }


  @ToString @EqualsAndHashCode public static class Method {
    @Getter @Setter(lombok.AccessLevel.PROTECTED) private String name;
    @Getter @Setter(lombok.AccessLevel.PROTECTED) private String signature;
    @Getter @Setter(lombok.AccessLevel.PROTECTED) private String owner;

    private Method() {
    }

    public static Method of(String name, String signature, String owner) {
      Method ret = new Method();
      ret.setName(name);
      ret.setSignature(signature);
      ret.setOwner(owner);
      return ret;
    }
  }

}
