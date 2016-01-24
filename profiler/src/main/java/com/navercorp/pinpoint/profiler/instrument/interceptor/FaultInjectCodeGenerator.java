package com.navercorp.pinpoint.profiler.instrument.interceptor;

import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.FaultInjectorInvokeHelper;

/**
 * Created by ychensha on 16/1/17.
 */
public class FaultInjectCodeGenerator extends InvokeBeforeCodeGenerator{
  public FaultInjectCodeGenerator(int interceptorId, InterceptorDefinition interceptorDefinition,
      InstrumentClass targetClass, InstrumentMethod targetMethod, TraceContext traceContext) {
    super(interceptorId, interceptorDefinition, targetClass, targetMethod, traceContext);
  }

  @Override
  protected  String getInterceptorInvokerHelperClassName() {
    return FaultInjectorInvokeHelper.class.getName();
  }
}
