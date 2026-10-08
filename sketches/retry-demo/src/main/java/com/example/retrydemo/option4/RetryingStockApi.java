package com.example.retrydemo.option4;

import com.example.retrydemo.client.StockApi;
import io.github.resilience4j.retry.Retry;

/**
 * OPTION 4a: a hand-written decorator around the client interface.
 * Explicit and easy to debug, at the cost of one delegating method per operation.
 */
public class RetryingStockApi implements StockApi {

    private final StockApi delegate;
    private final Retry retry;

    public RetryingStockApi(StockApi delegate, Retry retry) {
        this.delegate = delegate;
        this.retry = retry;
    }

    @Override
    public String getStock(String id, int simulatedFailures) {
        return Retry.decorateSupplier(retry, () -> delegate.getStock(id, simulatedFailures)).get();
    }
}
