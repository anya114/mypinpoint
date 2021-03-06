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

package com.navercorp.pinpoint.profiler.interceptor.bci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author emeroad
 */
public class TestObject {
  private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

  private int callA;
  private boolean isthrow = false;
  private int returnCode = 1;

  public void setIsthrow(boolean isthrow) {
    this.isthrow = isthrow;
  }

  public void setReturnCode(int returnCode) {
    this.returnCode = returnCode;
  }

  @Override
  public String toString() {
    return String.valueOf(callA);
  }

  public int callA() {

    logger.info("callA");
    int i = callA++;
    if (isthrow) {
      throw new RuntimeException("ddd");
    }
    System.out.println("testobject callA");
    internalMethod();
    TestObject2 testObject2 = new TestObject2();
    testObject2.callA();
    if(true) {
      throw new RuntimeException("callA exception");
    }
    return 0;
  }

  private int internalMethod() {
    int i = 1000;
    return i;
  }

  public static void before() {
    System.out.println("BEFORE");
  }

  public static void after() {
    System.out.println("AFTER");
  }

  public static void callCatch() {
    System.out.println("callCatch");
  }

  public String hello(String a) {
    System.out.println("a:" + a);
    System.out.println("test");
    // throw new RuntimeException("test");
    return "a";
  }

  public static void main(String[] args) {
    System.out.println(new TestObject().callA());
  }
}
