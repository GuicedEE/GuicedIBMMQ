package com.guicedee.ibmmq.implementations;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.guicedee.client.Environment;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.ibmmq.*;
import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.*;

/**
 * Guice module that binds IBM MQ connection factories, publishers, and consumers
 * based on discovered annotations at startup.
 */
@Log4j2
public class IBMMQModule extends AbstractModule implements IGuiceModule<IBMMQModule>
{
    @Getter
    private static final Map<String, ConnectionFactory> connectionFactories = new HashMap<>();

    @Override
    protected void configure()
    {
        Set<String> completedPublishers = new HashSet<>();

        IBMMQPreStartup.getPackageConnections().forEach((packageName, connections) -> {
            System.err.println("[IBMMQ-DEBUG] Package: " + packageName + " connections: " + connections.size());
            for (IBMMQConnectionOptions connectionOption : connections)
            {
                System.err.println("[IBMMQ-DEBUG] Creating CF for connection: " + connectionOption.value() + " host: " + connectionOption.host() + " port: " + connectionOption.port());
                try
                {
                    ConnectionFactory cf = createConnectionFactory(connectionOption);
                    connectionFactories.put(connectionOption.value(), cf);
                    System.err.println("[IBMMQ-DEBUG] CF created successfully for: " + connectionOption.value());
                    bind(Key.get(ConnectionFactory.class, Names.named(connectionOption.value()))).toInstance(cf);
                }
                catch (JMSException e)
                {
                    System.err.println("[IBMMQ-DEBUG] JMSException creating CF: " + e.getMessage());
                    log.error("Failed to create IBM MQ ConnectionFactory for connection '{}'", connectionOption.value(), e);
                }
                catch (Throwable e)
                {
                    System.err.println("[IBMMQ-DEBUG] Throwable creating CF: " + e.getClass().getName() + " - " + e.getMessage());
                    log.error("Unexpected error creating IBM MQ ConnectionFactory for connection '{}'", connectionOption.value(), e);
                    e.printStackTrace(System.err);
                }
            }
        });

        // Bind consumer classes
        IBMMQPreStartup.getQueueConsumerDefinitions().forEach((queueName, queueDef) -> {
            Class clazz = IBMMQPreStartup.getQueueConsumerClass().get(queueName);
            bind(clazz).in(Singleton.class);
            bind(Key.get(clazz, Names.named(queueName))).to(clazz);
            bind(Key.get(IBMMQConsumer.class, Names.named(queueName))).to(clazz);
        });

        // Bind publishers
        IBMMQPreStartup.getPackageConnections().forEach((packageName, connections) -> {
            for (IBMMQConnectionOptions connectionOption : connections)
            {
                var cf = connectionFactories.get(connectionOption.value());

                IBMMQPreStartup.getQueuePublisherDefinitions().forEach((queueName, queueDef) -> {
                    if (!completedPublishers.contains(queueName))
                    {
                        String connName = IBMMQPreStartup.getQueueConnectionNames().getOrDefault(queueName, "default");
                        if (connName.equals(connectionOption.value()) || (!connectionFactories.containsKey(connName) && cf != null))
                        {
                            completedPublishers.add(queueName);
                            bind(Key.get(IBMMQPublisher.class, Names.named(queueName)))
                                    .toProvider(() -> new IBMMQPublisher(cf, queueName, queueDef.options().topic(), queueDef.options()))
                                    .in(Singleton.class);
                        }
                    }
                });
            }
        });
    }

    /**
     * Creates an IBM MQ JMS ConnectionFactory from the annotation options.
     */
    public static ConnectionFactory createConnectionFactory(IBMMQConnectionOptions options) throws JMSException
    {
        JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER);
        JmsConnectionFactory cf = ff.createConnectionFactory();

        cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, options.host());
        cf.setIntProperty(WMQConstants.WMQ_PORT, options.port());

        if (!Strings.isNullOrEmpty(options.queueManager()))
        {
            cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, options.queueManager());
        }
        if (!Strings.isNullOrEmpty(options.channel()))
        {
            cf.setStringProperty(WMQConstants.WMQ_CHANNEL, options.channel());
        }

        // Transport type
        if ("bindings".equalsIgnoreCase(options.transportType()))
        {
            cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_BINDINGS);
        }
        else
        {
            cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
        }

        if (!Strings.isNullOrEmpty(options.applicationName()))
        {
            cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, options.applicationName());
        }

        if (!Strings.isNullOrEmpty(options.user()))
        {
            cf.setStringProperty(WMQConstants.USERID, options.user());
            cf.setStringProperty(WMQConstants.PASSWORD, options.password());
            cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        }

        if (options.sslEnabled())
        {
            if (!Strings.isNullOrEmpty(options.sslCipherSuite()))
            {
                cf.setStringProperty(WMQConstants.WMQ_SSL_CIPHER_SUITE, options.sslCipherSuite());
            }
            if (!Strings.isNullOrEmpty(options.sslPeerName()))
            {
                cf.setStringProperty(WMQConstants.WMQ_SSL_PEER_NAME, options.sslPeerName());
            }
        }

        if (options.connectionTimeout() > 0)
        {
            cf.setIntProperty(WMQConstants.WMQ_CONNECT_OPTIONS, options.connectionTimeout());
        }

        if (options.ccsid() > 0)
        {
            cf.setIntProperty(WMQConstants.WMQ_CCSID, options.ccsid());
        }

        if (!Strings.isNullOrEmpty(options.clientId()))
        {
            cf.setStringProperty(WMQConstants.CLIENT_ID, options.clientId());
        }

        log.info("Created IBM MQ ConnectionFactory for connection '{}' -> {}:{}/{}", options.value(), options.host(), options.port(), options.queueManager());
        return cf;
    }
}

