package com.navercorp.pinpoint.bootstrap.instrument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by ychensha on 16/1/25.
 */
@Target(ElementType.METHOD)
public @interface PinpointFaultInjected {
}
