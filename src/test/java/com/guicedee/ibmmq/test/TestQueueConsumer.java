package com.guicedee.ibmmq.test;

import com.guicedee.ibmmq.IBMMQConsumer;
import com.guicedee.ibmmq.IBMMQQueueDefinition;
import com.guicedee.ibmmq.IBMMQQueueOptions;
import com.google.inject.Singleton;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Test consumer for the DEV.QUEUE.1 queue.
 */
@IBMMQQueueDefinition(
        value = "DEV.QUEUE.1",
        options = @IBMMQQueueOptions(worker = true, autoAck = true)
)
@Singleton
public class TestQueueConsumer implements IBMMQConsumer
{
    private static final CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    public static void setLatch(CountDownLatch newLatch)
    {
        latch = newLatch;
    }

    public static List<String> getReceivedMessages()
    {
        return receivedMessages;
    }

    public static void clearMessages()
    {
        receivedMessages.clear();
    }

    @Override
    public void consume(Message message)
    {
        try
        {
            String text;
            if (message instanceof TextMessage tm)
            {
                text = tm.getText();
            }
            else
            {
                text = message.getJMSMessageID();
            }
            System.out.println("Consumed from DEV.QUEUE.1 - " + text);
            receivedMessages.add(text);
            latch.countDown();
        }
        catch (JMSException e)
        {
            throw new RuntimeException(e);
        }
    }
}

