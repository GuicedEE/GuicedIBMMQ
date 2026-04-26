package com.guicedee.ibmmq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tuning options for an IBM MQ queue consumer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IBMMQQueueOptions
{
    /**
     * @return Whether to auto-acknowledge messages (JMS AUTO_ACKNOWLEDGE).
     */
    boolean autoAck() default true;

    /**
     * @return Whether to process messages on a worker thread (blocking allowed).
     */
    boolean worker() default false;

    /**
     * @return Number of consumer instances to create.
     */
    int consumerCount() default 1;

    /**
     * @return Whether the consumer should use transacted sessions.
     */
    boolean transacted() default false;

    /**
     * @return Whether the consumer should be automatically started on startup.
     */
    boolean autobind() default true;

    /**
     * @return Message receive timeout in milliseconds (0 for no-wait, -1 for infinite).
     */
    long receiveTimeoutMs() default 1000;

    /**
     * @return Whether to use a topic destination instead of a queue.
     */
    boolean topic() default false;

    /**
     * @return Subscription name for durable topic subscriptions (empty for non-durable).
     */
    String durableSubscription() default "";

    /**
     * @return Message selector expression (empty for no filter).
     */
    String messageSelector() default "";

    /**
     * @return Message persistence mode: 0=non-persistent, 1=persistent, -1=default.
     */
    int persistence() default -1;

    /**
     * @return Message priority (0-9, -1 for default).
     */
    int priority() default -1;

    /**
     * @return Time-to-live in milliseconds for published messages (0 for unlimited).
     */
    long timeToLive() default 0;
}

