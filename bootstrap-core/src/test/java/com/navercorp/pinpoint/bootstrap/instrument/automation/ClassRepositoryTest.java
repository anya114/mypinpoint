package com.navercorp.pinpoint.bootstrap.instrument.automation;

/**
 * Created by ychensha on 16/1/24.
 */
import org.junit.Test;

import static org.junit.Assert.*;

public class ClassRepositoryTest {
  @Test public void testFindOne() throws Exception {
    ClassRepository.ClassId newId =
        ClassRepository.ClassId.of(this.getClass().getName(), getClass().getClassLoader());
    ClassRepository.INSTANCE.add(newId, new ClassRepository.ClassMirror(newId));
    assertTrue(ClassRepository.INSTANCE.findOne(newId).isPresent());
  }

  @Test public void testFindByEqualId() throws Exception {
    ClassRepository.ClassId newId =
        ClassRepository.ClassId.of(this.getClass().getName(), getClass().getClassLoader());
    ClassRepository.INSTANCE.add(newId, new ClassRepository.ClassMirror(newId));
    ClassRepository.ClassMirror newIdMirror = ClassRepository.INSTANCE.findOne(newId).get();
    String equalName = new String("com.navercorp.pinpoint.bootstrap.instrument.automation.ClassRepositoryTest");
    assertEquals(equalName, this.getClass().getName());
    ClassRepository.ClassId equalId =
        ClassRepository.ClassId.of(equalName, getClass().getClassLoader());
    assertTrue(ClassRepository.INSTANCE.findOne(equalId).isPresent());
//    ClassRepository.INSTANCE.add(equalId, new ClassRepository.ClassMirror(equalId));
//    assertEquals(newIdMirror, );
  }

}
