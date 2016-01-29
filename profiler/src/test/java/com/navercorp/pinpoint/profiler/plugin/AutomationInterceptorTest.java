package com.navercorp.pinpoint.profiler.plugin;

import com.google.common.io.Files;
import com.navercorp.pinpoint.bootstrap.config.DefaultProfilerConfig;
import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.instrument.*;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.registry.InterceptorRegistry;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.automation.AutomationInterceptor;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.profiler.DefaultAgent;
import com.navercorp.pinpoint.profiler.context.DefaultTrace;
import com.navercorp.pinpoint.profiler.context.storage.Storage;
import com.navercorp.pinpoint.profiler.interceptor.bci.TestObject;
import com.navercorp.pinpoint.profiler.logging.Slf4jLoggerBinder;
import com.navercorp.pinpoint.test.MockAgent;
import com.navercorp.pinpoint.test.TestClassLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.asm.tree.analysis.Interpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * AutomationInterceptor Tester.
 *
 * @author ychensha
 * @version 1.0
 * @since <pre>一月 24, 2016</pre>
 */
public class AutomationInterceptorTest {
  private Logger logger = LoggerFactory.getLogger(this.getClass());

  private TestClassLoader getTestClassLoader() throws IOException {
    PLoggerFactory.initialize(new Slf4jLoggerBinder());

    Properties properties = new Properties();
    properties.load(getClass().getClassLoader().getResourceAsStream("pinpoint.config"));
    ProfilerConfig profilerConfig = new DefaultProfilerConfig(properties);
    profilerConfig.setApplicationServerType(ServiceType.TEST_STAND_ALONE.getName());
    DefaultAgent agent = MockAgent.of(profilerConfig);

    return new TestClassLoader(agent);
  }

  @Before public void before() throws Exception {
  }

  @After public void after() throws Exception {
  }

  @Test public void testOn() throws Exception {
    final TestClassLoader loader = getTestClassLoader();
    final String javassistClassName = "com.navercorp.pinpoint.profiler.interceptor.bci.TestObject";
    loader.addTransformer(javassistClassName, new TransformCallback() {

      @Override public byte[] doInTransform(Instrumentor instrumentContext, ClassLoader classLoader,
          String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
          byte[] classfileBuffer) throws InstrumentException {
        try {
          logger.info("modify className:{} cl:{}", className, classLoader);

          InstrumentClass aClass = instrumentContext
              .getInstrumentClass(classLoader, javassistClassName, classfileBuffer);

          String methodName = "callA";
          aClass.getDeclaredMethod(methodName)
              .addInterceptor(AutomationInterceptor.class.getName());

          return aClass.toBytecode();
        } catch (InstrumentException e) {
          e.printStackTrace();
          throw new RuntimeException(e.getMessage(), e);
        }
      }
    });

    loader.initialize();

    Class<?> testObjectClazz = loader.loadClass(javassistClassName);
    final String methodName = "callA";
    logger.info("class:{}", testObjectClazz.toString());
    final Object testObject = testObjectClazz.newInstance();
    Method callA = testObjectClazz.getMethod(methodName);
    AutomationInterceptor interceptor =
        (AutomationInterceptor) InterceptorRegistry.getInterceptor(0);
    for (int i = 0; i < 1025; i++) {
      callA.invoke(testObject);
    }
    assertNotNull(interceptor);
    assertEquals(false, interceptor.isOn());
  }

  @Test public void testConcurrent() throws Exception {
    final TestClassLoader loader = getTestClassLoader();
    final String javassistClassName = TestObject.class.getName();
    final String methodName = "callA";
    loader.addTransformer(javassistClassName, new TransformCallback() {

      @Override public byte[] doInTransform(Instrumentor instrumentContext, ClassLoader classLoader,
          String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
          byte[] classfileBuffer) throws InstrumentException {
        try {
          logger.info("modify className:{} cl:{}", className, classLoader);

          InstrumentClass aClass = instrumentContext
              .getInstrumentClass(classLoader, javassistClassName, classfileBuffer);

          aClass.getDeclaredMethod(methodName)
              .addInterceptor(AutomationInterceptor.class.getName());

          return aClass.toBytecode();
        } catch (InstrumentException e) {
        }
        return null;
      }
    });

    loader.initialize();

    Class<?> testObjectClazz = loader.loadClass(javassistClassName);
    final Object testObject = testObjectClazz.newInstance();
    final Method callA = testObjectClazz.getMethod(methodName);
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    for(int i = 0; i < 5000; i++) {
      executorService.submit(new Runnable() {
        @Override public void run() {
          try {
            callA.invoke(testObject);
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          } catch (InvocationTargetException e) {
            e.printStackTrace();
          }
        }
      });
    }
    try {
      executorService.awaitTermination(10, TimeUnit.SECONDS);
    }catch (InterruptedException e) {

    }
    AutomationInterceptor interceptor =
        (AutomationInterceptor) InterceptorRegistry.getInterceptor(0);
    assertFalse(interceptor.isOn());
  }

  private Storage getStorage(DefaultTrace trace) {
    try {
      Field storage = DefaultTrace.class.getDeclaredField("storage");
      storage.setAccessible(true);
      return (Storage) storage.get(trace);
    } catch (Exception e) {

    }
    return null;
  }

  @Test public void testWithException() throws Exception {
    TestClassLoader loader = getTestClassLoader();
    final String testClassName = TestObject.class.getName();
    final String methodName = "callA";
    loader.addTransformer(testClassName, new TransformCallback() {
      @Override public byte[] doInTransform(Instrumentor instrumentor, ClassLoader classLoader,
          String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
          byte[] classfileBuffer) throws InstrumentException {
        logger.info("modify className:{} cl:{}", className, classLoader);
        InstrumentClass target =
            instrumentor.getInstrumentClass(classLoader, testClassName, classfileBuffer);
        for (InstrumentMethod method : target.getDeclaredMethods(MethodFilters.name(methodName))) {
          method.addInterceptor(AutomationInterceptor.class.getName());
        }
        byte[] newClassFile = target.toBytecode();
        try {
          Files.write(newClassFile, new File("TestObject.class"));
        } catch (IOException e) {
        }
        return newClassFile;
      }
    });
    loader.initialize();
    Class<?> testObjectClass = loader.loadClass(testClassName);
    loader.loadClass(testClassName);
    loader.loadClass(testClassName);
    Method method = testObjectClass.getMethod(methodName);
    method.invoke(testObjectClass.newInstance());
  }

  /**
   * Method: after(Object target, Object[] args, Object result, Throwable throwable)
   */
  @Test public void testAfter() throws Exception {
    //TODO: Test goes here...
  }

  /**
   * Method: getDescriptor()
   */
  @Test public void testGetDescriptor() throws Exception {
    //TODO: Test goes here...
  }


  /**
   * Method: recordRootSpan(final SpanRecorder recorder)
   */
  @Test public void testRecordRootSpan() throws Exception {
    //TODO: Test goes here...
/* 
try { 
   Method method = AutomationInterceptor.getClass().getMethod("recordRootSpan", final.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/
  }

} 
