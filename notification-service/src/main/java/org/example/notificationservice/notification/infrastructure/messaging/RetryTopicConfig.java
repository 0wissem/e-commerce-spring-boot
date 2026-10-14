package org.example.notificationservice.notification.infrastructure.messaging;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Puts the FAILURE REASON back on dead-letter records, under header names WE own.
 *
 * Switching from blocking retries to retry topics quietly lost it: the old DefaultErrorHandler's
 * recoverer stamped kafka_dlt-exception-* headers on every dead letter, while the retry-topic
 * recoverer forwards only kafka_original-topic by default. A test caught it — the DLT handler was
 * logging "reason=[null: null]".
 *
 * That reason is the whole value of a parking lot: a human opens the record and has to see WHY it
 * died without digging through logs from three days ago. Re-enabled explicitly here.
 */
@Configuration
public class RetryTopicConfig extends RetryTopicConfigurationSupport {

    /**
     * Required BECAUSE of the subclass above: extending RetryTopicConfigurationSupport makes Boot's
     * own retry-topic auto-configuration back off, and the scheduler it used to provide disappears
     * with it ("Either a RetryTopicSchedulerWrapper or TaskScheduler bean is required").
     *
     * This scheduler is what makes the retries non-blocking: a record on the retry topic is held
     * until its delay elapses, here, instead of on a consumer thread.
     */
    @Bean
    public RetryTopicSchedulerWrapper retryTopicScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stock-low-retry-backoff-");
        return new RetryTopicSchedulerWrapper(scheduler);
    }

    /**
     * Stamps the failure reason onto every forwarded record.
     *
     * The ROOT cause, not the wrapper: what reaches here is a ListenerExecutionFailedException
     * wrapping the real problem, and "listener failed" tells an operator nothing.
     */
    @Override
    protected Consumer<DeadLetterPublishingRecovererFactory> configureDeadLetterPublishingContainerFactory() {
        return factory -> factory.setHeadersFunction((record, exception) -> {
            Throwable cause = rootCause(exception);
            Headers headers = new RecordHeaders();
            headers.add(FailureHeaders.FAILURE_CLASS, bytes(cause.getClass().getName()));
            headers.add(FailureHeaders.FAILURE_MESSAGE, bytes(String.valueOf(cause.getMessage())));
            return headers;
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
