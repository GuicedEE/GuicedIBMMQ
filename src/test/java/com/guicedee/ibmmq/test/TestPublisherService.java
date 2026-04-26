package com.guicedee.ibmmq.test;

import com.guicedee.ibmmq.IBMMQPublisher;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Test service that injects publishers by queue name,
 * validating that field-based publisher discovery works.
 */
public class TestPublisherService
{
    @Inject
    @Named("DEV.QUEUE.1")
    private IBMMQPublisher queue1Publisher;

    @Inject
    @Named("DEV.QUEUE.2")
    private IBMMQPublisher queue2Publisher;

    public IBMMQPublisher getQueue1Publisher()
    {
        return queue1Publisher;
    }

    public IBMMQPublisher getQueue2Publisher()
    {
        return queue2Publisher;
    }
}

