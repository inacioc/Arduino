package com.example.retrydemo.option3;

import org.springframework.http.client.ClientHttpResponse;

/**
 * Signals "this response is a 5xx, please retry" to Resilience4j from inside the interceptor.
 * At interceptor level a 5xx is a normal response, not an exception (RestClient's status
 * handlers run later), so we have to turn it into an exception ourselves.
 *
 * It carries the response so that, when attempts are exhausted, the interceptor can hand
 * the last response back and let RestClient raise its usual HttpServerErrorException.
 */
public class RetryableStatusException extends RuntimeException {

    private final transient ClientHttpResponse response;

    public RetryableStatusException(ClientHttpResponse response) {
        super("Retryable HTTP status");
        this.response = response;
    }

    public ClientHttpResponse getResponse() {
        return response;
    }
}
