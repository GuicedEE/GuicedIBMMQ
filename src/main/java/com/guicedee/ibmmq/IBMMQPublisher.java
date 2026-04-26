package com.guicedee.ibmmq;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.jms.*;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j2;

/**
 * Publishes messages to an IBM MQ queue or topic. Bound via Guice with {@code @Named("queue-name")}.
 */
@JsonSerialize(as = Void.class)
@EqualsAndHashCode(of = {"destinationName"})
@Log4j2
public class IBMMQPublisher
{
    private final ConnectionFactory connectionFactory;
    private final String destinationName;
    private final boolean isTopic;
    private final IBMMQQueueOptions queueOptions;

    private volatile JMSContext jmsContext;

    /**
     * Creates a publisher bound to a specific destination.
     *
     * @param connectionFactory The JMS connection factory.
     * @param destinationName   The queue or topic name to publish to.
     * @param isTopic           Whether the destination is a topic.
     * @param queueOptions      The queue options for message properties.
     */
    public IBMMQPublisher(ConnectionFactory connectionFactory, String destinationName, boolean isTopic, IBMMQQueueOptions queueOptions)
    {
        this.connectionFactory = connectionFactory;
        this.destinationName = destinationName;
        this.isTopic = isTopic;
        this.queueOptions = queueOptions;
    }

    /**
     * Sends a text message to the configured destination.
     *
     * @param body The message body.
     */
    public void publish(String body)
    {
        try
        {
            JMSContext ctx = getOrCreateContext();
            Destination destination = isTopic ? ctx.createTopic(destinationName) : ctx.createQueue(destinationName);
            JMSProducer producer = ctx.createProducer();

            if (queueOptions.persistence() >= 0)
            {
                producer.setDeliveryMode(queueOptions.persistence() == 0 ? DeliveryMode.NON_PERSISTENT : DeliveryMode.PERSISTENT);
            }
            if (queueOptions.priority() >= 0)
            {
                producer.setPriority(queueOptions.priority());
            }
            if (queueOptions.timeToLive() > 0)
            {
                producer.setTimeToLive(queueOptions.timeToLive());
            }

            producer.send(destination, body);
            log.trace("Message published to {} '{}'", isTopic ? "topic" : "queue", destinationName);
        }
        catch (JMSRuntimeException e)
        {
            log.error("Failed to publish message to {} '{}'", isTopic ? "topic" : "queue", destinationName, e);
        }
    }

    /**
     * Sends a text message with a correlation ID.
     *
     * @param body          The message body.
     * @param correlationId The JMS correlation ID.
     */
    public void publish(String body, String correlationId)
    {
        try
        {
            JMSContext ctx = getOrCreateContext();
            Destination destination = isTopic ? ctx.createTopic(destinationName) : ctx.createQueue(destinationName);
            JMSProducer producer = ctx.createProducer();

            if (queueOptions.persistence() >= 0)
            {
                producer.setDeliveryMode(queueOptions.persistence() == 0 ? DeliveryMode.NON_PERSISTENT : DeliveryMode.PERSISTENT);
            }
            if (queueOptions.priority() >= 0)
            {
                producer.setPriority(queueOptions.priority());
            }
            if (queueOptions.timeToLive() > 0)
            {
                producer.setTimeToLive(queueOptions.timeToLive());
            }

            producer.setJMSCorrelationID(correlationId);
            producer.send(destination, body);
            log.trace("Message published to {} '{}' with correlationId={}", isTopic ? "topic" : "queue", destinationName, correlationId);
        }
        catch (JMSRuntimeException e)
        {
            log.error("Failed to publish message to {} '{}'", isTopic ? "topic" : "queue", destinationName, e);
        }
    }

    /**
     * Sends a bytes message.
     *
     * @param body The message body as bytes.
     */
    public void publish(byte[] body)
    {
        try
        {
            JMSContext ctx = getOrCreateContext();
            Destination destination = isTopic ? ctx.createTopic(destinationName) : ctx.createQueue(destinationName);
            JMSProducer producer = ctx.createProducer();

            if (queueOptions.persistence() >= 0)
            {
                producer.setDeliveryMode(queueOptions.persistence() == 0 ? DeliveryMode.NON_PERSISTENT : DeliveryMode.PERSISTENT);
            }
            if (queueOptions.priority() >= 0)
            {
                producer.setPriority(queueOptions.priority());
            }
            if (queueOptions.timeToLive() > 0)
            {
                producer.setTimeToLive(queueOptions.timeToLive());
            }

            producer.send(destination, body);
            log.trace("Bytes message published to {} '{}'", isTopic ? "topic" : "queue", destinationName);
        }
        catch (JMSRuntimeException e)
        {
            log.error("Failed to publish bytes message to {} '{}'", isTopic ? "topic" : "queue", destinationName, e);
        }
    }

    /**
     * @return The destination name this publisher targets.
     */
    public String getDestinationName()
    {
        return destinationName;
    }

    /**
     * Closes the underlying JMS context.
     */
    public void close()
    {
        if (jmsContext != null)
        {
            try
            {
                jmsContext.close();
            }
            catch (Exception e)
            {
                log.warn("Error closing JMS context for '{}'", destinationName, e);
            }
        }
    }

    private JMSContext getOrCreateContext()
    {
        if (jmsContext == null)
        {
            synchronized (this)
            {
                if (jmsContext == null)
                {
                    jmsContext = connectionFactory.createContext();
                }
            }
        }
        return jmsContext;
    }
}

