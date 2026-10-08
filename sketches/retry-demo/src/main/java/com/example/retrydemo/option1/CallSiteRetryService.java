package com.example.retrydemo.option1;

import com.example.retrydemo.client.StockApi;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * OPTION 1: wrap the call site.
 *
 * Resilience4j does not know anything about HTTP. It decorates a Supplier, so we only
 * need a place where we call the client, not access to the RestClient itself.
 */
@Service
public class CallSiteRetryService {

    private final StockApi client;
    private final Retry retry;

    public CallSiteRetryService(@Qualifier("plainClient") StockApi client, RetryRegistry registry) {
        this.client = client;
        this.retry = registry.retry("callsite");
    }

    public String getStock(String id, int simulatedFailures) {
        return Retry.decorateSupplier(retry, () -> client.getStock(id, simulatedFailures)).get();
    }
}
