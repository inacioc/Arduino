package com.example.retrydemo.client;

import org.springframework.web.client.RestClient;

/**
 * Stands in for an SDK or shared library: it builds its own RestClient internally,
 * and none of our code can reach that instance afterwards.
 *
 * Its only extension point is the RestClient.Builder it accepts, which is also what
 * real SDKs usually expose.
 */
public class ThirdPartyStockClient implements StockApi {

    private final RestClient restClient;

    public ThirdPartyStockClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String getStock(String id, int simulatedFailures) {
        return restClient.get()
                .uri("/stock/{id}?failures={failures}", id, simulatedFailures)
                .retrieve()
                .body(String.class);
    }
}
