package com.navercorp.pinpoint.bootstrap.faultinject;

import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Created by ychensha on 16/1/17.
 */
public class FaultInjector implements AroundInterceptor {
  private final FaultType type;

  public FaultInjector(FaultType type) {
    this.type = type;
  }
  @Override public void before(Object target, Object[] args) {
    type.doInject();
  }

  @Override public void after(Object target, Object[] args, Object result, Throwable throwable) {
    //ignore
  }

  public enum FaultType {
    Sleep {
      @Override public void doInject() {
        try {
          TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {

        }
      }
    }, Exception {
      @Override public void doInject() {
        throw new RuntimeException("injected Exception");
      }
    };

    public abstract void doInject();
  }
}
