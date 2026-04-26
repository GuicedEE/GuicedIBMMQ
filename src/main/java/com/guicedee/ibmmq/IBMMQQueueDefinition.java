package com.guicedee.ibmmq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an IBM MQ queue for consumption or publishing.
 * Place on a class that implements {@link IBMMQConsumer} or on a publisher field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface IBMMQQueueDefinition
{
    /**
     * @return The queue name to consume from or publish to.
     */
    String value();

    /**
     * @return Queue options for this definition.
     */
    IBMMQQueueOptions options() default @IBMMQQueueOptions;

    /**
     * @return The connection name to use, or {@code "default"} for the package connection.
     */
    String connection() default "default";
}

