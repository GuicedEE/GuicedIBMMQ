/**
 * IBM MQ test messaging package.
 * Connection options are declared at the package level.
 */
@IBMMQConnectionOptions(
        value = "test-connection",
        host = "localhost",
        port = 1414,
        queueManager = "QM1",
        channel = "DEV.APP.SVRCONN",
        user = "app",
        password = "passw0rd"
)
package com.guicedee.ibmmq.test;

import com.guicedee.ibmmq.IBMMQConnectionOptions;

