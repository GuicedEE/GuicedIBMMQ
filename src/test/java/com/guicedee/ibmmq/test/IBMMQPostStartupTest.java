package com.guicedee.ibmmq.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.ibmmq.IBMMQPublisher;
import com.guicedee.ibmmq.implementations.IBMMQPreStartup;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the IBM MQ GuicedEE module using Testcontainers.
 * <p>
 * Uses the official IBM MQ developer image ({@code icr.io/ibm-messaging/mq:latest})
 * which pre-creates queue manager {@code QM1}, channel {@code DEV.APP.SVRCONN},
 * and queues {@code DEV.QUEUE.1} through {@code DEV.QUEUE.5}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IBMMQPostStartupTest
{
    private static GenericContainer<?> mqContainer;

    @BeforeAll
    static void startMQ()
    {
        mqContainer = new GenericContainer<>(DockerImageName.parse("icr.io/ibm-messaging/mq:latest"))
                .withExposedPorts(1414, 9443)
                .withEnv("LICENSE", "accept")
                .withEnv("MQ_QMGR_NAME", "QM1")
                .withEnv("MQ_APP_PASSWORD", "passw0rd")
                .waitingFor(new LogMessageWaitStrategy()
                        .withRegEx(".*Started web server.*\\n")
                        .withStartupTimeout(Duration.ofMinutes(3)));

        mqContainer.start();

        // Override the port via env so the annotation env override picks it up
        Integer mappedPort = mqContainer.getMappedPort(1414);
        String mappedHost = mqContainer.getHost();
        System.setProperty("IBMMQ_HOST", mappedHost);
        System.setProperty("IBMMQ_PORT", String.valueOf(mappedPort));

        // Register modules for ClassGraph scanning
        IGuiceContext.registerModule("com.guicedee.ibmmq");
        IGuiceContext.registerModule("com.guicedee.ibmmq.test");
    }

    @AfterAll
    static void stopMQ()
    {
        if (mqContainer != null)
        {
            mqContainer.stop();
        }
        System.clearProperty("IBMMQ_HOST");
        System.clearProperty("IBMMQ_PORT");
    }

    @Test
    @Order(1)
    void testProduceAndConsumeMessage() throws Exception
    {
        TestQueueConsumer.clearMessages();
        CountDownLatch latch = new CountDownLatch(1);
        TestQueueConsumer.setLatch(latch);

        IGuiceContext.instance()
                .getConfig()
                .setClasspathScanning(true)
                .setAnnotationScanning(true)
                .setFieldScanning(true);

        IGuiceContext.instance().inject();

        // Get the publisher
        IBMMQPublisher publisher = IGuiceContext.get(Key.get(IBMMQPublisher.class, Names.named("DEV.QUEUE.1")));
        assertNotNull(publisher, "Publisher for DEV.QUEUE.1 should be bound");

        // Send a message
        publisher.publish("Hello IBM MQ from GuicedEE!");

        // Wait for consumer to receive the message
        boolean received = latch.await(30, TimeUnit.SECONDS);
        assertTrue(received, "Consumer should have received the message within 30 seconds");

        List<String> messages = TestQueueConsumer.getReceivedMessages();
        assertFalse(messages.isEmpty(), "Should have received at least one message");
        assertEquals("Hello IBM MQ from GuicedEE!", messages.get(0));
    }

    @Test
    @Order(2)
    void testMultipleMessages() throws Exception
    {
        TestQueueConsumer.clearMessages();
        int messageCount = 5;
        CountDownLatch latch = new CountDownLatch(messageCount);
        TestQueueConsumer.setLatch(latch);

        IBMMQPublisher publisher = IGuiceContext.get(Key.get(IBMMQPublisher.class, Names.named("DEV.QUEUE.1")));

        for (int i = 0; i < messageCount; i++)
        {
            publisher.publish("message_" + i);
        }

        boolean allReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(allReceived, "All " + messageCount + " messages should be received");
        assertEquals(messageCount, TestQueueConsumer.getReceivedMessages().size());
    }

    @Test
    @Order(3)
    void testTransactedConsumer() throws Exception
    {
        TransactedQueueConsumer.clearMessages();
        CountDownLatch latch = new CountDownLatch(1);
        TransactedQueueConsumer.setLatch(latch);

        IBMMQPublisher publisher = IGuiceContext.get(Key.get(IBMMQPublisher.class, Names.named("DEV.QUEUE.2")));
        assertNotNull(publisher, "Publisher for DEV.QUEUE.2 should be bound");

        publisher.publish("Transacted message!");

        boolean received = latch.await(30, TimeUnit.SECONDS);
        assertTrue(received, "Transacted consumer should have received the message");

        List<String> messages = TransactedQueueConsumer.getReceivedMessages();
        assertFalse(messages.isEmpty());
        assertEquals("Transacted message!", messages.get(0));
    }

    @Test
    @Order(4)
    void testPublishWithCorrelationId() throws Exception
    {
        TestQueueConsumer.clearMessages();
        CountDownLatch latch = new CountDownLatch(1);
        TestQueueConsumer.setLatch(latch);

        IBMMQPublisher publisher = IGuiceContext.get(Key.get(IBMMQPublisher.class, Names.named("DEV.QUEUE.1")));
        publisher.publish("Correlated message", "CORR-001");

        boolean received = latch.await(30, TimeUnit.SECONDS);
        assertTrue(received, "Message with correlation ID should be received");
        assertTrue(TestQueueConsumer.getReceivedMessages().contains("Correlated message"));
    }

    @Test
    @Order(5)
    void testPublishBytesMessage() throws Exception
    {
        // Bytes message goes to DEV.QUEUE.1 but our text consumer won't decode it as text.
        // We just verify it doesn't throw.
        IBMMQPublisher publisher = IGuiceContext.get(Key.get(IBMMQPublisher.class, Names.named("DEV.QUEUE.1")));
        assertDoesNotThrow(() -> publisher.publish("bytes payload".getBytes()));
    }

    @Test
    @Order(6)
    void testPublisherServiceInjection()
    {
        TestPublisherService service = IGuiceContext.get(TestPublisherService.class);
        assertNotNull(service, "Service should be injectable");
        assertNotNull(service.getQueue1Publisher(), "DEV.QUEUE.1 publisher should be injected");
        assertNotNull(service.getQueue2Publisher(), "DEV.QUEUE.2 publisher should be injected");
    }

    @Test
    @Order(7)
    void testPackageInfoConnectionDiscovery()
    {
        assertFalse(IBMMQPreStartup.getPackageConnections().isEmpty(),
                "Should have discovered at least one package-level connection");

        boolean foundTestConnection = IBMMQPreStartup.getPackageConnections().values().stream()
                .flatMap(List::stream)
                .anyMatch(conn -> "test-connection".equals(conn.value()));
        assertTrue(foundTestConnection, "Should have found 'test-connection' from package-info.java");
    }

    @Test
    @Order(8)
    void testConsumerAndPublisherDiscovery()
    {
        assertTrue(IBMMQPreStartup.getQueueConsumerDefinitions().containsKey("DEV.QUEUE.1"),
                "DEV.QUEUE.1 consumer should be discovered");
        assertTrue(IBMMQPreStartup.getQueueConsumerDefinitions().containsKey("DEV.QUEUE.2"),
                "DEV.QUEUE.2 consumer should be discovered");
        assertTrue(IBMMQPreStartup.getQueuePublisherDefinitions().containsKey("DEV.QUEUE.1"),
                "DEV.QUEUE.1 publisher should be discovered");
        assertTrue(IBMMQPreStartup.getQueuePublisherDefinitions().containsKey("DEV.QUEUE.2"),
                "DEV.QUEUE.2 publisher should be discovered");
    }
}

