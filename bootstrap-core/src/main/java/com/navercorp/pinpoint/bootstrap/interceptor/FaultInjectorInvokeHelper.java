package com.navercorp.pinpoint.bootstrap.interceptor;

/**
 * Created by apple on 16/1/17.
 */
public class FaultInjectorInvokeHelper {
  /**
   * always propagate Exception
   * @param t which fault injector throws
   */
  public static void handleException(Throwable t) {
    throw new RuntimeException(t);
  }
}
