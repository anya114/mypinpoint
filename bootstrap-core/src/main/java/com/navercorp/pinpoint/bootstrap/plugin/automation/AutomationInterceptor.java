package com.navercorp.pinpoint.bootstrap.plugin.automation;


import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.SystemClock;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class AutomationInterceptor implements AroundInterceptor {
  private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
  private static final AutomationMethodDescriptor AUTOMATION_METHOD_DESCRIPTOR =
      new AutomationMethodDescriptor();
  private final boolean isDebug = logger.isDebugEnabled();
  @Getter
  private final TraceContext traceContext;
  private final InstrumentMethod instrumentMethod;
  private boolean called;
  @Getter private MethodDescriptor descriptor;

  private AtomicInteger count = new AtomicInteger(0);
  private LongAdder execSum = new LongAdder();
  @Getter
  private boolean on = true;
  private long beforeTimeStamp;
  private int maxCount = 3;
  //ms
  private int minExecTime = 3;

  public AutomationInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor,
      InstrumentMethod instrumentMethod) {
    this.traceContext = traceContext;
    this.descriptor = methodDescriptor;
    this.instrumentMethod = instrumentMethod;
    traceContext.cacheApi(AUTOMATION_METHOD_DESCRIPTOR);
  }

  @Override public void before(Object target, Object[] args) {
    if (isDebug) {
      logger.beforeInterceptor(target, args);
    }
    if (!called) {
      called = true;
      instrumentMethod.instrument();
    }
    if (!on) {
      return;
    }

    Trace trace = traceContext.currentTraceObject();
    if (trace == null) {
      trace = traceContext.newTraceObject(TraceType.USER);
      if (!trace.canSampled()) {
        if (isDebug) {
          logger.debug("New trace and can't sampled {}", trace);
        }
        return;
      }
      if (isDebug) {
        logger.debug("New trace and sampled {}", trace);
      }
      SpanRecorder recorder = trace.getSpanRecorder();
      recordRootSpan(recorder);
    }
    trace.traceBlockBegin();
    if(count.get() < maxCount) {
      beforeTimeStamp = System.currentTimeMillis();
    }
  }

  private void recordRootSpan(final SpanRecorder recorder) {
    // root
    recorder.recordServiceType(ServiceType.STAND_ALONE);
    recorder.recordApi(AUTOMATION_METHOD_DESCRIPTOR);
  }

  @Override public void after(Object target, Object[] args, Object result, Throwable throwable) {
    // TODO Auto-generated method stub
    if (isDebug) {
      logger.afterInterceptor(target, args);
    }
    if (!on) {
      return;
    }
    if(count.get() < maxCount) {
      long now = SystemClock.INSTANCE.getTime();
      execSum.add(now - beforeTimeStamp);
      logger.debug("AutomationInterceptor record time elapsed: {}", now - beforeTimeStamp);
      beforeTimeStamp = now;
      if(count.incrementAndGet() == maxCount) {
        if(execSum.intValue() / maxCount < minExecTime) {
          on = false;
        }
      }
    }
    final Trace trace = traceContext.currentTraceObject();
    if (trace == null) {
      return;
    }

    try {
      SpanEventRecorder recorder = trace.currentSpanEventRecorder();
      recorder.recordApi(descriptor);
      recorder.recordException(throwable);
    } finally {
      trace.traceBlockEnd();
      if (trace.getTraceType() == TraceType.USER && trace.isRootStack()) {
        if (isDebug) {
          logger.debug("Closed user trace. {}", trace);
        }
        trace.close();
        traceContext.removeTraceObject();
      }
    }
  }

}
