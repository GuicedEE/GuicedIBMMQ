module com.guicedee.ibmmq.test {
    requires com.guicedee.ibmmq;
    requires com.guicedee.client;
    requires com.guicedee.guicedinjection;
    requires com.google.guice;

    requires org.junit.jupiter.api;
    requires org.testcontainers;
    requires org.apache.commons.lang3;

    requires jakarta.messaging;

    opens com.guicedee.ibmmq.test to com.google.guice, com.fasterxml.jackson.databind, io.github.classgraph, org.junit.platform.commons;
}

