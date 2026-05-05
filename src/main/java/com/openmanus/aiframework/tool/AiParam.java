package com.openmanus.aiframework.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines metadata for a tool method parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AiParam {

    /**
     * Human-readable parameter description.
     */
    String value() default "";

    /**
     * Optional parameter name override. Defaults to compiled parameter name.
     */
    String name() default "";

    /**
     * Whether this parameter is required in tool arguments.
     */
    boolean required() default true;
}
