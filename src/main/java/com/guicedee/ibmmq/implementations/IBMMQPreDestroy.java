package com.guicedee.ibmmq.implementations;

import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import lombok.extern.log4j.Log4j2;

/**
 * Cleans up IBM MQ JMS consumers, publishers, and connections on application shutdown.
 */
@Log4j2
public class IBMMQPreDestroy implements IGuicePreDestroy<IBMMQPreDestroy>
{
    @Override
    public void onDestroy()
    {
        log.info("Shutting down IBM MQ consumers and connections...");

        // Close all JMS consumers
        IBMMQPostStartup.getJmsConsumers().forEach((key, consumer) -> {
            try
            {
                consumer.close();
                log.debug("IBM MQ consumer '{}' closed.", key);
            }
            catch (Exception e)
            {
                log.error("Error closing IBM MQ consumer '{}'", key, e);
            }
        });

        // Close all JMS contexts
        IBMMQPostStartup.getConsumerContexts().forEach((key, context) -> {
            try
            {
                context.close();
                log.debug("IBM MQ JMS context '{}' closed.", key);
            }
            catch (Exception e)
            {
                log.error("Error closing IBM MQ JMS context '{}'", key, e);
            }
        });

        log.info("IBM MQ shutdown complete.");
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MAX_VALUE - 100;
    }
}

