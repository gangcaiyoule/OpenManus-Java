package com.openmanus.aiframework.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an executable agent tool.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AiTool {

    /**
     * Human-readable tool description.
     */
    String value() default "";

    /**
     * Optional tool name override. Defaults to method name.
     */
    String name() default "";
}
