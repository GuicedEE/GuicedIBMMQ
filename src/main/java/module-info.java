import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.ibmmq.implementations.*;

module com.guicedee.ibmmq {
    exports com.guicedee.ibmmq;
    exports com.guicedee.ibmmq.implementations;

    requires transitive com.ibm.mq.jakarta;
    requires com.guicedee.vertx;
    requires com.guicedee.client;
    requires static lombok;

    requires io.github.classgraph;
    requires org.apache.commons.lang3;

    provides IGuicePostStartup with IBMMQPostStartup;
    provides IGuiceModule with IBMMQModule;
    provides IGuicePreStartup with IBMMQPreStartup;
    provides IGuicePreDestroy with IBMMQPreDestroy;

    opens com.guicedee.ibmmq to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.ibmmq.implementations to com.fasterxml.jackson.databind, com.google.guice;
}

