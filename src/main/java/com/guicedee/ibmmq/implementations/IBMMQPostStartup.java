package com.guicedee.ibmmq.implementations;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.scopes.CallScoper;
import com.guicedee.client.scopes.CallScopeProperties;
import com.guicedee.client.scopes.CallScopeSource;
import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import com.guicedee.ibmmq.*;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.jms.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Post-startup initializer that creates JMS consumers for IBM MQ queues and topics.
 */
@Log4j2
public class IBMMQPostStartup implements IGuicePostStartup<IBMMQPostStartup>
{
    @Inject
    private Vertx vertx;

    @Getter
    private static final Map<String, JMSContext> consumerContexts = new HashMap<>();

    @Getter
    private static final Map<String, JMSConsumer> jmsConsumers = new HashMap<>();

    private static final ExecutorService consumerExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ibmmq-consumer-poller");
        t.setDaemon(true);
        return t;
    });

    @Override
    public List<Uni<Boolean>> postLoad()
    {
        return List.of(Uni.createFrom().item(() -> {
            IBMMQPreStartup.getQueueConsumerDefinitions().forEach((queueName, queueDef) -> {
                String connectionName = IBMMQPreStartup.getQueueConnectionNames().getOrDefault(queueName, "default");
                ConnectionFactory cf = IBMMQModule.getConnectionFactories().get(connectionName);

                if (cf == null)
                {
                    // Fall back to first available
                    cf = IBMMQModule.getConnectionFactories().values().stream().findFirst().orElse(null);
                }

                if (cf == null)
                {
                    log.error("No ConnectionFactory found for IBM MQ consumer on queue '{}', connection '{}'", queueName, connectionName);
                    return;
                }

                Class<? extends IBMMQConsumer> consumerClass = IBMMQPreStartup.getQueueConsumerClass().get(queueName);
                if (consumerClass == null)
                {
                    log.warn("No consumer class found for queue '{}', skipping.", queueName);
                    return;
                }

                IBMMQQueueOptions options = queueDef.options();
                if (!options.autobind())
                {
                    log.debug("IBM MQ consumer for queue '{}' has autobind=false, skipping auto-start.", queueName);
                    return;
                }

                for (int i = 0; i < options.consumerCount(); i++)
                {
                    startConsumer(cf, queueName, queueDef, consumerClass, options, i);
                }
            });
            return true;
        }));
    }

    private void startConsumer(ConnectionFactory cf, String queueName, IBMMQQueueDefinition queueDef,
                               Class<? extends IBMMQConsumer> consumerClass, IBMMQQueueOptions options, int index)
    {
        try
        {
            int sessionMode = options.transacted() ? JMSContext.SESSION_TRANSACTED :
                    (options.autoAck() ? JMSContext.AUTO_ACKNOWLEDGE : JMSContext.CLIENT_ACKNOWLEDGE);

            JMSContext jmsContext = cf.createContext(sessionMode);
            String consumerKey = queueName + "_" + index;
            consumerContexts.put(consumerKey, jmsContext);

            Destination destination;
            if (options.topic())
            {
                destination = jmsContext.createTopic(queueName);
            }
            else
            {
                destination = jmsContext.createQueue(queueName);
            }

            JMSConsumer consumer;
            String selector = options.messageSelector().isEmpty() ? null : options.messageSelector();

            if (options.topic() && !options.durableSubscription().isEmpty())
            {
                consumer = jmsContext.createDurableConsumer((Topic) destination, options.durableSubscription() + "_" + index, selector, false);
            }
            else
            {
                consumer = selector != null ? jmsContext.createConsumer(destination, selector) : jmsContext.createConsumer(destination);
            }

            jmsConsumers.put(consumerKey, consumer);

            // Start polling in a dedicated thread via Vert.x worker
            consumerExecutor.submit(() -> pollMessages(consumer, queueName, queueDef, consumerClass, options, jmsContext));

            log.info("IBM MQ consumer started for {} '{}' (instance {})", options.topic() ? "topic" : "queue", queueName, index);
        }
        catch (JMSRuntimeException e)
        {
            log.error("Failed to start IBM MQ consumer for queue '{}' (instance {})", queueName, index, e);
        }
    }

    private void pollMessages(JMSConsumer consumer, String queueName, IBMMQQueueDefinition queueDef,
                              Class<? extends IBMMQConsumer> consumerClass, IBMMQQueueOptions options, JMSContext jmsContext)
    {
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                Message message = consumer.receive(options.receiveTimeoutMs());
                if (message != null)
                {
                    if (options.worker())
                    {
                        vertx.executeBlocking(() -> {
                            executeConsumer(message, queueName, queueDef, consumerClass, options, jmsContext);
                            return true;
                        }, false);
                    }
                    else
                    {
                        executeConsumer(message, queueName, queueDef, consumerClass, options, jmsContext);
                    }
                }
            }
            catch (JMSRuntimeException e)
            {
                if (!Thread.currentThread().isInterrupted())
                {
                    log.error("Error receiving message from queue '{}'", queueName, e);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private void executeConsumer(Message message, String queueName, IBMMQQueueDefinition queueDef,
                                 Class<? extends IBMMQConsumer> consumerClass, IBMMQQueueOptions options, JMSContext jmsContext)
    {
        CallScoper scopedRunner = null;
        boolean started = false;
        try
        {
            scopedRunner = IGuiceContext.get(CallScoper.class);
            if (!scopedRunner.isStartedScope())
            {
                scopedRunner.enter();
                started = true;
            }

            var properties = IGuiceContext.get(CallScopeProperties.class);
            properties.setSource(CallScopeSource.IBMMQ);

            log.trace("Processing IBM MQ message from {} '{}'", options.topic() ? "topic" : "queue", queueName);
            var mqConsumer = IGuiceContext.get(Key.get(IBMMQConsumer.class, Names.named(queueName)));
            mqConsumer.consume(message);

            if (options.transacted())
            {
                jmsContext.commit();
            }
            else if (!options.autoAck())
            {
                message.acknowledge();
            }
        }
        catch (Throwable t)
        {
            log.error("Error processing IBM MQ message for queue '{}'", queueName, t);
            if (options.transacted())
            {
                try { jmsContext.rollback(); } catch (Exception e) { log.error("Rollback failed for queue '{}'", queueName, e); }
            }
        }
        finally
        {
            if (started && scopedRunner != null)
            {
                scopedRunner.exit();
            }
        }
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MAX_VALUE - 200;
    }
}

