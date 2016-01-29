package com.navercorp.pinpoint.bootstrap.plugin.automation;


import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.SystemClock;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class AutomationInterceptor implements AroundInterceptor {
  private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
  private static final AutomationMethodDescriptor AUTOMATION_METHOD_DESCRIPTOR =
      new AutomationMethodDescriptor();
  private final boolean isDebug = logger.isDebugEnabled();
  @Getter private final TraceContext traceContext;
  private final InstrumentMethod instrumentMethod;
  private AtomicBoolean called = new AtomicBoolean(false);
  @Getter private MethodDescriptor descriptor;

  private AtomicInteger count = new AtomicInteger(0);
  private LongAdder execSum = new LongAdder();
  private AtomicBoolean on = new AtomicBoolean(true);
  private ThreadLocal<Long> beforeTimeStamp = ThreadLocal.withInitial(new Supplier<Long>() {
    @Override public Long get() {
      return Long.valueOf(0);
    }
  });
  private static final int maxCount = 500;
  //ms
  private static final int minExecTime = 3;

  public AutomationInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor,
      InstrumentMethod instrumentMethod) {
    this.traceContext = traceContext;
    this.descriptor = methodDescriptor;
    this.instrumentMethod = instrumentMethod;
    traceContext.cacheApi(AUTOMATION_METHOD_DESCRIPTOR);
  }

  public boolean isOn() {
    return on.get();
  }

  @Override public void before(Object target, Object[] args) {

    if (isDebug) {
      logger.beforeInterceptor(target, args);
    }
    if (!called.get() && called.compareAndSet(false, true)) {
      instrumentMethod.instrument();
    }
    if (!on.get()) {
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
    if (count.get() < maxCount) {
      beforeTimeStamp.set(System.currentTimeMillis());
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
    if (!on.get()) {
      return;
    }
    if (count.get() < maxCount) {
      long now = SystemClock.INSTANCE.getTime();
      execSum.add(now - beforeTimeStamp.get());
      logger.debug("AutomationInterceptor record time elapsed: {}", now - beforeTimeStamp.get());
      beforeTimeStamp.set(now);
      if (count.incrementAndGet() == maxCount) {
        if (execSum.intValue() / maxCount < minExecTime) {
          on.compareAndSet(true, false);
        } else {
          logger.debug("AutomationInterceptor keep on. avrg time: {}", execSum.intValue() / maxCount);
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
