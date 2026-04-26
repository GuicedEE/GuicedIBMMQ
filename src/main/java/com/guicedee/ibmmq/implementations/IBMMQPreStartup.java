package com.guicedee.ibmmq.implementations;

import com.google.common.base.Strings;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.ibmmq.*;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;
import com.google.inject.Key;
import com.google.inject.name.Names;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-startup scanner that discovers IBM MQ annotations and registers
 * queues, consumers, and publishers for later binding.
 */
@Log4j2
public class IBMMQPreStartup implements IGuicePreStartup<IBMMQPreStartup>
{
    /**
     * Package-level connection options.
     */
    @Getter
    private static final Map<String, List<IBMMQConnectionOptions>> packageConnections = new TreeMap<>();

    /**
     * Queue consumer definitions keyed by queue name.
     */
    @Getter
    private static final Map<String, IBMMQQueueDefinition> queueConsumerDefinitions = new HashMap<>();

    /**
     * Consumer classes keyed by queue name.
     */
    @Getter
    private static final Map<String, Class<? extends IBMMQConsumer>> queueConsumerClass = new HashMap<>();

    /**
     * Consumer Guice keys keyed by queue name.
     */
    @Getter
    private static final Map<String, Key<?>> queueConsumerKeys = new HashMap<>();

    /**
     * Queue publisher definitions keyed by queue name.
     */
    @Getter
    private static final Map<String, IBMMQQueueDefinition> queuePublisherDefinitions = new HashMap<>();

    /**
     * Publisher Guice keys keyed by queue name.
     */
    @Getter
    private static final Map<String, Key<?>> queuePublisherKeys = new HashMap<>();

    /**
     * Maps queue name to connection name.
     */
    @Getter
    private static final Map<String, String> queueConnectionNames = new HashMap<>();

    /**
     * Maps package name to connection name.
     */
    @Getter
    private static final Map<String, String> packageConnectionNames = new HashMap<>();

    @Override
    public List<Future<Boolean>> onStartup()
    {
        return List.of(VertXPreStartup.getVertx().executeBlocking(() -> {
            ScanResult scanResult = IGuiceContext.instance().getScanResult();
            Set<Class<?>> completedConsumers = new HashSet<>();
            processConnections(scanResult, completedConsumers);
            return true;
        }));
    }

    private void processConnections(ScanResult scanResult, Set<Class<?>> completedConsumers)
    {
        // Check classes annotated with @IBMMQConnectionOptions
        ClassInfoList connectionClasses = scanResult.getClassesWithAnnotation(IBMMQConnectionOptions.class);
        connectionClasses.stream()
                .distinct()
                .forEach(ci -> processConnection(scanResult, ci, completedConsumers, false));
        connectionClasses.stream()
                .distinct()
                .forEach(ci -> processConnection(scanResult, ci, completedConsumers, true));

        // Check package-info annotations
        var packageInfoList = scanResult.getPackageInfo();
        if (packageInfoList != null)
        {
            for (var pkgInfo : packageInfoList)
            {
                try
                {
                    Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                    var ann = pkgClass.getAnnotation(IBMMQConnectionOptions.class);
                    if (ann != null)
                    {
                        log.debug("Found @IBMMQConnectionOptions on package: {}", pkgInfo.getName());
                        String connectionName = ann.value();
                        IBMMQConnectionOptions wrapped = wrapConnectionOptions(connectionName, ann);
                        packageConnections.computeIfAbsent(pkgInfo.getName(), k -> new ArrayList<>()).add(wrapped);
                        packageConnectionNames.put(pkgInfo.getName(), connectionName);

                        // Process consumers and publishers in this package
                        var classInfos = pkgInfo.getClassInfoRecursive();
                        processConsumers(classInfos, connectionName, completedConsumers);
                        processPublishers(classInfos, connectionName);
                    }
                }
                catch (ClassNotFoundException ignored) {}
            }
        }
    }

    private void processConnection(ScanResult scanResult, ClassInfo classInfo, Set<Class<?>> completedConsumers, boolean publishers)
    {
        log.debug("Found IBM MQ Connection - {}", classInfo.getName());

        var connectionAnnotation = classInfo.loadClass().getAnnotation(IBMMQConnectionOptions.class);
        String connectionName = connectionAnnotation.value();
        IBMMQConnectionOptions wrapped = wrapConnectionOptions(connectionName, connectionAnnotation);
        packageConnections.computeIfAbsent(classInfo.getPackageName(), k -> new ArrayList<>()).add(wrapped);
        packageConnectionNames.put(classInfo.getPackageName(), connectionName);

        var classInfos = scanResult.getPackageInfo(classInfo.getPackageName()).getClassInfoRecursive();

        if (!publishers)
        {
            processConsumers(classInfos, connectionName, completedConsumers);
        }
        else
        {
            processPublishers(classInfos, connectionName);
        }
    }

    @SuppressWarnings("unchecked")
    private void processConsumers(List<ClassInfo> classInfos, String connectionName, Set<Class<?>> completedConsumers)
    {
        var consumers = classInfos.stream()
                .filter(info -> info.hasAnnotation(IBMMQQueueDefinition.class))
                .filter(info -> info.implementsInterface(IBMMQConsumer.class))
                .distinct()
                .toList();

        for (ClassInfo consumerClassInfo : consumers)
        {
            Class<? extends IBMMQConsumer> consumerClass = (Class<? extends IBMMQConsumer>) consumerClassInfo.loadClass();
            if (completedConsumers.contains(consumerClass))
            {
                continue;
            }
            completedConsumers.add(consumerClass);

            var queueDef = consumerClass.getAnnotation(IBMMQQueueDefinition.class);
            String queueName = queueDef.value();
            IBMMQQueueDefinition wrappedDef = wrapQueueDefinition(queueDef);

            queueConsumerDefinitions.put(queueName, wrappedDef);
            queueConsumerClass.put(queueName, consumerClass);
            queueConsumerKeys.put(queueName, Key.get(consumerClass, Names.named(queueName)));
            queueConnectionNames.putIfAbsent(queueName, connectionName);

            // Also register publisher for this queue
            registerPublisher(queueName, connectionName, wrappedDef);

            log.debug("Found IBM MQ Consumer - {} - {}", queueName, connectionName);
        }
    }

    private void processPublishers(List<ClassInfo> classInfos, String connectionName)
    {
        classInfos.stream()
                .filter(info -> info.hasDeclaredFieldAnnotation(IBMMQQueueDefinition.class) ||
                        info.getFieldInfo().stream()
                                .anyMatch(a -> a.getTypeSignatureOrTypeDescriptor().toString().equals("com.guicedee.ibmmq.IBMMQPublisher")))
                .forEach(publisherClassInfo -> registerFieldPublishers(publisherClassInfo, connectionName));
    }

    private void registerFieldPublishers(ClassInfo publisherClassInfo, String connectionName)
    {
        var fields = getPublisherFields(publisherClassInfo);
        for (Field field : fields)
        {
            String queueName = getQueueNameFromField(field, publisherClassInfo);
            if (queueName != null && !queuePublisherDefinitions.containsKey(queueName))
            {
                var queueDef = field.getAnnotation(IBMMQQueueDefinition.class);
                IBMMQQueueDefinition wrappedDef = queueDef != null ? wrapQueueDefinition(queueDef) : createDefaultQueueDefinition(queueName);
                registerPublisher(queueName, connectionName, wrappedDef);
                log.debug("Found IBM MQ Publisher - {} - {}", queueName, connectionName);
            }
        }
    }

    private void registerPublisher(String queueName, String connectionName, IBMMQQueueDefinition wrappedDef)
    {
        queuePublisherDefinitions.putIfAbsent(queueName, wrappedDef);
        queuePublisherKeys.putIfAbsent(queueName, Key.get(IBMMQPublisher.class, Names.named(queueName)));
        queueConnectionNames.putIfAbsent(queueName, connectionName);
    }

    private String getQueueNameFromField(Field field, ClassInfo publisherClassInfo)
    {
        if (field.isAnnotationPresent(Named.class))
        {
            return field.getAnnotation(Named.class).value();
        }
        else if (field.isAnnotationPresent(com.google.inject.name.Named.class))
        {
            return field.getAnnotation(com.google.inject.name.Named.class).value();
        }
        else
        {
            var queueDef = publisherClassInfo.loadClass().getAnnotation(IBMMQQueueDefinition.class);
            return queueDef != null ? queueDef.value() : null;
        }
    }

    private List<Field> getPublisherFields(ClassInfo aClass)
    {
        Class<?> clazz = aClass.loadClass();
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()))
                .filter(field -> {
                    boolean hasInject = field.isAnnotationPresent(com.google.inject.Inject.class) || field.isAnnotationPresent(jakarta.inject.Inject.class);
                    boolean hasNamed = field.isAnnotationPresent(Named.class) || field.isAnnotationPresent(com.google.inject.name.Named.class);
                    return hasInject && hasNamed && field.getType().equals(IBMMQPublisher.class);
                })
                .collect(Collectors.toList());
    }

    private IBMMQQueueDefinition createDefaultQueueDefinition(String queueName)
    {
        return new IBMMQQueueDefinition()
        {
            @Override
            public Class<? extends Annotation> annotationType() { return IBMMQQueueDefinition.class; }

            @Override
            public String value() { return envForName(queueName, "QUEUE_NAME", queueName); }

            @Override
            public IBMMQQueueOptions options() { return new IBMMQQueueOptionsDefault(); }

            @Override
            public String connection() { return "default"; }
        };
    }

    private IBMMQQueueDefinition wrapQueueDefinition(IBMMQQueueDefinition def)
    {
        if (def == null) return null;
        String rawName = def.value();
        return new IBMMQQueueDefinition()
        {
            @Override
            public Class<? extends Annotation> annotationType() { return IBMMQQueueDefinition.class; }

            @Override
            public String value() { return envForName(rawName, "QUEUE_NAME", def.value()); }

            @Override
            public IBMMQQueueOptions options() { return wrapQueueOptions(rawName, def.options()); }

            @Override
            public String connection() { return envForName(rawName, "QUEUE_CONNECTION", def.connection()); }
        };
    }

    private IBMMQQueueOptions wrapQueueOptions(String queueName, IBMMQQueueOptions options)
    {
        if (options == null) return null;
        return new IBMMQQueueOptions()
        {
            @Override
            public Class<? extends Annotation> annotationType() { return IBMMQQueueOptions.class; }

            @Override
            public boolean autoAck() { return Boolean.parseBoolean(envForName(queueName, "QUEUE_AUTO_ACK", String.valueOf(options.autoAck()))); }

            @Override
            public boolean worker() { return Boolean.parseBoolean(envForName(queueName, "QUEUE_WORKER", String.valueOf(options.worker()))); }

            @Override
            public int consumerCount() { return Integer.parseInt(envForName(queueName, "QUEUE_CONSUMER_COUNT", String.valueOf(options.consumerCount()))); }

            @Override
            public boolean transacted() { return Boolean.parseBoolean(envForName(queueName, "QUEUE_TRANSACTED", String.valueOf(options.transacted()))); }

            @Override
            public boolean autobind() { return Boolean.parseBoolean(envForName(queueName, "QUEUE_AUTOBIND", String.valueOf(options.autobind()))); }

            @Override
            public long receiveTimeoutMs() { return Long.parseLong(envForName(queueName, "QUEUE_RECEIVE_TIMEOUT_MS", String.valueOf(options.receiveTimeoutMs()))); }

            @Override
            public boolean topic() { return Boolean.parseBoolean(envForName(queueName, "QUEUE_TOPIC", String.valueOf(options.topic()))); }

            @Override
            public String durableSubscription() { return envForName(queueName, "QUEUE_DURABLE_SUBSCRIPTION", options.durableSubscription()); }

            @Override
            public String messageSelector() { return envForName(queueName, "QUEUE_MESSAGE_SELECTOR", options.messageSelector()); }

            @Override
            public int persistence() { return Integer.parseInt(envForName(queueName, "QUEUE_PERSISTENCE", String.valueOf(options.persistence()))); }

            @Override
            public int priority() { return Integer.parseInt(envForName(queueName, "QUEUE_PRIORITY", String.valueOf(options.priority()))); }

            @Override
            public long timeToLive() { return Long.parseLong(envForName(queueName, "QUEUE_TIME_TO_LIVE", String.valueOf(options.timeToLive()))); }
        };
    }

    @SuppressWarnings("unchecked")
    private IBMMQConnectionOptions wrapConnectionOptions(String connectionName, IBMMQConnectionOptions ann)
    {
        return new IBMMQConnectionOptions()
        {
            @Override
            public Class<? extends Annotation> annotationType() { return IBMMQConnectionOptions.class; }

            @Override
            public String value() { return envForName(connectionName, "CONNECTION_NAME", ann.value()); }

            @Override
            public String host() { return envForName(connectionName, "HOST", ann.host()); }

            @Override
            public int port() { return Integer.parseInt(envForName(connectionName, "PORT", String.valueOf(ann.port()))); }

            @Override
            public String queueManager() { return envForName(connectionName, "QUEUE_MANAGER", ann.queueManager()); }

            @Override
            public String channel() { return envForName(connectionName, "CHANNEL", ann.channel()); }

            @Override
            public String user() { return envForName(connectionName, "USER", ann.user()); }

            @Override
            public String password() { return envForName(connectionName, "PASSWORD", ann.password()); }

            @Override
            public String applicationName() { return envForName(connectionName, "APPLICATION_NAME", ann.applicationName()); }

            @Override
            public String transportType() { return envForName(connectionName, "TRANSPORT_TYPE", ann.transportType()); }

            @Override
            public boolean sslEnabled() { return Boolean.parseBoolean(envForName(connectionName, "SSL_ENABLED", String.valueOf(ann.sslEnabled()))); }

            @Override
            public String sslCipherSuite() { return envForName(connectionName, "SSL_CIPHER_SUITE", ann.sslCipherSuite()); }

            @Override
            public String sslPeerName() { return envForName(connectionName, "SSL_PEER_NAME", ann.sslPeerName()); }

            @Override
            public int connectionTimeout() { return Integer.parseInt(envForName(connectionName, "CONNECTION_TIMEOUT", String.valueOf(ann.connectionTimeout()))); }

            @Override
            public int ccsid() { return Integer.parseInt(envForName(connectionName, "CCSID", String.valueOf(ann.ccsid()))); }

            @Override
            public int reconnectAttempts() { return Integer.parseInt(envForName(connectionName, "RECONNECT_ATTEMPTS", String.valueOf(ann.reconnectAttempts()))); }

            @Override
            public long reconnectIntervalMs() { return Long.parseLong(envForName(connectionName, "RECONNECT_INTERVAL_MS", String.valueOf(ann.reconnectIntervalMs()))); }

            @Override
            public String clientId() { return envForName(connectionName, "CLIENT_ID", ann.clientId()); }
        };
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 80;
    }

    /**
     * Resolves an environment variable or system property scoped by name.
     * Lookup order:
     * 1. IBMMQ_{NORMALIZED_NAME}_{PROPERTY}
     * 2. IBMMQ_{PROPERTY}
     * 3. defaultValue
     */
    static String envForName(String name, String property, String defaultValue)
    {
        String normalizedName = name.toUpperCase().replace('-', '_').replace('.', '_');
        String scopedKey = "IBMMQ_" + normalizedName + "_" + property;
        String scopedValue = com.guicedee.client.Environment.getSystemPropertyOrEnvironment(scopedKey, null);
        if (scopedValue != null && !scopedValue.isBlank())
        {
            return scopedValue;
        }
        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("IBMMQ_" + property, defaultValue);
    }
}

