package com.navercorp.pinpoint.bootstrap.plugin.automation;


import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.common.trace.ServiceType;
import lombok.Getter;

public class AutomationInterceptor implements AroundInterceptor {
  private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
  private static final AutomationMethodDescriptor AUTOMATION_METHOD_DESCRIPTOR =
      new AutomationMethodDescriptor();
  private final boolean isDebug = logger.isDebugEnabled();
  private final TraceContext traceContext;
  private final InstrumentMethod instrumentMethod;
  private boolean called;
  @Getter private MethodDescriptor descriptor;

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
      if (isDebug) {
        logger.debug("Closed user trace. {}", trace.getCallStackFrameId());
      }
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
