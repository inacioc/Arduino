package com.example.retrydemo.config;

import com.example.retrydemo.option3.RetryableStatusException;
import com.example.retrydemo.option3.RetryingClientHttpRequestInterceptor;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableResilientMethods // switches on Spring Framework 7's @Retryable (option 2)
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /**
     * One shared policy, reused by options 1, 3 and 4 and by the tests.
     *
     * - 3 attempts in total (1 call + 2 retries)
     * - exponential backoff with jitter, so many clients do not retry at the same instant
     * - retry on network failures and 5xx, never on 4xx (retrying a 404 cannot help)
     */
    public static RetryConfig retryConfig(Duration initialInterval) {
        return RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(initialInterval, 2.0, 0.5))
                .retryExceptions(
                        IOException.class,                // raw I/O errors seen inside an interceptor
                        ResourceAccessException.class,    // I/O errors as wrapped by RestClient
                        HttpServerErrorException.class,   // 5xx as raised by RestClient
                        RetryableStatusException.class)   // 5xx detected inside our interceptor
                .ignoreExceptions(HttpClientErrorException.class) // 4xx
                .build();
    }

    @Bean
    RetryRegistry retryRegistry(@Value("${demo.retry.initial-interval-ms:200}") long initialIntervalMs) {
        RetryRegistry registry = RetryRegistry.of(retryConfig(Duration.ofMillis(initialIntervalMs)));

        // Log every retry so you can watch the behaviour in the console.
        registry.getEventPublisher().onEntryAdded(added ->
                added.getAddedEntry().getEventPublisher().onRetry(event ->
                        log.info("[retry:{}] attempt #{} failed ({}), waiting {} ms",
                                event.getName(),
                                event.getNumberOfRetryAttempts(),
                                event.getLastThrowable(),
                                event.getWaitInterval().toMillis())));
        return registry;
    }

    /**
     * OPTION 3: every RestClient.Builder that Spring Boot hands out now carries the retry
     * interceptor. Code that builds its client with RestClient.builder() (static factory)
     * is NOT affected, which is exactly why options 1, 2 and 4 use "plainClient".
     *
     * Do not mix levels: a retry here plus a retry at the call site multiplies attempts
     * (3 x 3 = 9 calls against an upstream that is already struggling).
     */
    @Bean
    RestClientCustomizer retryingRestClientCustomizer(RetryRegistry registry) {
        return builder -> builder.requestInterceptor(
                new RetryingClientHttpRequestInterceptor(registry.retry("interceptor")));
    }
}
