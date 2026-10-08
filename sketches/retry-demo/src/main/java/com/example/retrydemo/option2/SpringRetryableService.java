package com.example.retrydemo.option2;

import com.example.retrydemo.client.StockApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * OPTION 2: declarative retry with an annotation, using Spring Framework 7's built-in
 * support (@Retryable + @EnableResilientMethods, enabled in ResilienceConfig).
 *
 * With Spring Boot 4 you may not need Resilience4j for plain retries any more. Resilience4j
 * remains the better pick when you also want circuit breakers, bulkheads and metrics together.
 *
 * Pitfalls shared with every proxy-based annotation (including Resilience4j's own @Retry):
 *  - calling this method from inside the same class bypasses the proxy, so no retry happens;
 *  - the class must be a Spring bean and must not be final.
 *
 * maxRetries = 2 means 1 initial call + 2 retries = 3 attempts in total.
 */
@Service
public class SpringRetryableService {

    private final StockApi client;

    public SpringRetryableService(@Qualifier("plainClient") StockApi client) {
        this.client = client;
    }

    @Retryable(
            includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetries = 2,
            delay = 50)
    public String getStock(String id, int simulatedFailures) {
        return client.getStock(id, simulatedFailures);
    }
}
