package com.navercorp.pinpoint.profiler.plugin;

import com.navercorp.pinpoint.bootstrap.config.DefaultProfilerConfig;
import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.context.Trace;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.interceptor.registry.InterceptorRegistry;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.automation.AutomationInterceptor;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.profiler.DefaultAgent;
import com.navercorp.pinpoint.profiler.context.DefaultTrace;
import com.navercorp.pinpoint.profiler.context.storage.Storage;
import com.navercorp.pinpoint.profiler.logging.Slf4jLoggerBinder;
import com.navercorp.pinpoint.test.MockAgent;
import com.navercorp.pinpoint.test.TestClassLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
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

  /**
   * Method: before(Object target, Object[] args)
   */
  @Test public void testBefore() throws Exception {
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
    for (int i = 0; i < 5; i++) {
      callA.invoke(testObject);
    }
    assertNotNull(interceptor);
    assertEquals(false, interceptor.isOn());
    System.out.println(getStorage((DefaultTrace) interceptor.getTraceContext().currentTraceObject()));
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
