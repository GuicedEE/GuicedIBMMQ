# Guiced IBM MQ

Annotation-driven IBM MQ integration for GuicedEE using the IBM MQ JMS client.

## Quick Start

1. Add `com.guicedee:ibmmq` dependency
2. Define a connection on `package-info.java` or any class:
   ```java
   @IBMMQConnectionOptions(
       value = "my-connection",
       host = "localhost",
       port = 1414,
       queueManager = "QM1",
       channel = "DEV.APP.SVRCONN"
   )
   package com.example.messaging;
   ```
3. Create a consumer:
   ```java
   @IBMMQQueueDefinition("DEV.QUEUE.1")
   public class OrderConsumer implements IBMMQConsumer {
       @Override
       public void consume(Message message) {
           // process message
       }
   }
   ```
4. Inject a publisher:
   ```java
   @Inject @Named("DEV.QUEUE.1")
   private IBMMQPublisher publisher;
   ```
5. Configure `module-info.java`:
   ```java
   module my.app {
       requires com.guicedee.ibmmq;
       opens my.app.messaging to com.google.guice, com.guicedee.ibmmq;
   }
   ```

## Environment Variable Overrides

Every annotation attribute can be overridden at runtime:
- `IBMMQ_{NORMALIZED_NAME}_{PROPERTY}` — name-specific override
- `IBMMQ_{PROPERTY}` — global fallback

## License

Apache License 2.0

