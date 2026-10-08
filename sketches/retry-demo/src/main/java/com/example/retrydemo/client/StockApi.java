package com.example.retrydemo.client;

/**
 * The contract of the "third-party" client. Think of it as an SDK interface you did not write.
 */
public interface StockApi {

    /**
     * @param simulatedFailures only used by the demo upstream ("fail this many times, then succeed").
     *                          A real SDK would not have this parameter.
     */
    String getStock(String id, int simulatedFailures);
}
