package com.guicedee.ibmmq.implementations;

import com.guicedee.ibmmq.IBMMQQueueOptions;

import java.lang.annotation.Annotation;

/**
 * Default mutable implementation of {@link IBMMQQueueOptions} used when no annotation is present.
 */
public class IBMMQQueueOptionsDefault implements IBMMQQueueOptions
{
    @Override
    public Class<? extends Annotation> annotationType() { return IBMMQQueueOptions.class; }

    @Override
    public boolean autoAck() { return true; }

    @Override
    public boolean worker() { return false; }

    @Override
    public int consumerCount() { return 1; }

    @Override
    public boolean transacted() { return false; }

    @Override
    public boolean autobind() { return true; }

    @Override
    public long receiveTimeoutMs() { return 1000; }

    @Override
    public boolean topic() { return false; }

    @Override
    public String durableSubscription() { return ""; }

    @Override
    public String messageSelector() { return ""; }

    @Override
    public int persistence() { return -1; }

    @Override
    public int priority() { return -1; }

    @Override
    public long timeToLive() { return 0; }

    @Override
    public boolean equals(Object obj) { return true; }

    @Override
    public int hashCode() { return "0".hashCode(); }
}

