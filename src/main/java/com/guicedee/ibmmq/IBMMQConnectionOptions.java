package com.guicedee.ibmmq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares connection-level configuration for an IBM MQ queue manager.
 * Place on a class or {@code package-info.java}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface IBMMQConnectionOptions
{
    /**
     * @return Logical name of this connection for diagnostics and bindings.
     */
    String value() default "default";

    /**
     * @return Hostname or IP address of the queue manager.
     */
    String host() default "localhost";

    /**
     * @return Listener port of the queue manager.
     */
    int port() default 1414;

    /**
     * @return Queue manager name.
     */
    String queueManager() default "";

    /**
     * @return Channel name for client connections.
     */
    String channel() default "DEV.APP.SVRCONN";

    /**
     * @return User name for authentication.
     */
    String user() default "";

    /**
     * @return Password for authentication.
     */
    String password() default "";

    /**
     * @return Application name for connection identification.
     */
    String applicationName() default "";

    /**
     * @return Transport type: "client" (default) or "bindings".
     */
    String transportType() default "client";

    /**
     * @return Whether to use TLS/SSL for connections.
     */
    boolean sslEnabled() default false;

    /**
     * @return SSL cipher suite name (e.g., "TLS_RSA_WITH_AES_256_CBC_SHA256").
     */
    String sslCipherSuite() default "";

    /**
     * @return SSL peer name for certificate verification.
     */
    String sslPeerName() default "";

    /**
     * @return Connection timeout in milliseconds (0 for default).
     */
    int connectionTimeout() default 0;

    /**
     * @return CCSID (Coded Character Set Identifier) for message encoding (0 for default).
     */
    int ccsid() default 0;

    /**
     * @return Number of reconnect attempts on connection failure.
     */
    int reconnectAttempts() default 3;

    /**
     * @return Delay in milliseconds between reconnect attempts.
     */
    long reconnectIntervalMs() default 5000;

    /**
     * @return Client ID for the connection (empty for auto-generated).
     */
    String clientId() default "";
}

