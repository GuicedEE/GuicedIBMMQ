package com.guicedee.ibmmq;

import jakarta.jms.Message;

/**
 * Contract for consuming messages from an IBM MQ queue or topic.
 */
public interface IBMMQConsumer
{
    /**
     * Handles a single JMS message from IBM MQ.
     *
     * @param message The received JMS message.
     */
    void consume(Message message);
}

