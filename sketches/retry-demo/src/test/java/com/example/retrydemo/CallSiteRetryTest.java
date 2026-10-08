package com.example.retrydemo;

import com.example.retrydemo.client.ThirdPartyStockClient;
import com.example.retrydemo.config.ResilienceConfig;
import com.example.retrydemo.option1.CallSiteRetryService;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Option 1: the retry wraps the call site; the client itself is a plain, untouched one. */
class CallSiteRetryTest {

    static FlakyServer server;
    CallSiteRetryService service;

    @BeforeAll
    static void start() throws IOException {
        server = new FlakyServer();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @BeforeEach
    void setUp() {
        var client = new ThirdPartyStockClient(RestClient.builder(), server.baseUrl());
        var registry = RetryRegistry.of(ResilienceConfig.retryConfig(Duration.ofMillis(1)));
        service = new CallSiteRetryService(client, registry);
    }

    @Test
    void retriesTransient503ThenSucceeds() {
        server.reset(2, 503);

        assertThat(service.getStock("42", 0)).isEqualTo("stock-42");
        assertThat(server.hits()).isEqualTo(3); // 1 call + 2 retries
    }

    @Test
    void givesUpAfterMaxAttempts() {
        server.reset(10, 503);

        assertThatThrownBy(() -> service.getStock("42", 0)).isInstanceOf(HttpServerErrorException.class);
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        server.reset(10, 404);

        assertThatThrownBy(() -> service.getStock("42", 0)).isInstanceOf(HttpClientErrorException.class);
        assertThat(server.hits()).isEqualTo(1);
    }
}
