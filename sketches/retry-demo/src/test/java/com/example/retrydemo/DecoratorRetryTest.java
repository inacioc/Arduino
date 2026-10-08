package com.example.retrydemo;

import com.example.retrydemo.client.StockApi;
import com.example.retrydemo.client.ThirdPartyStockClient;
import com.example.retrydemo.config.ResilienceConfig;
import com.example.retrydemo.option4.RetryProxy;
import com.example.retrydemo.option4.RetryingStockApi;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Option 4: decorate the client interface, by hand (4a) or with a generic dynamic proxy (4b). */
class DecoratorRetryTest {

    static FlakyServer server;
    StockApi plain;
    Retry retry;

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
        plain = new ThirdPartyStockClient(RestClient.builder(), server.baseUrl());
        retry = Retry.of("decorator-test", ResilienceConfig.retryConfig(Duration.ofMillis(1)));
    }

    @Test
    void handWrittenDecoratorRetries() {
        server.reset(2, 503);

        assertThat(new RetryingStockApi(plain, retry).getStock("42", 0)).isEqualTo("stock-42");
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void dynamicProxyRetries() {
        server.reset(2, 503);
        StockApi proxied = RetryProxy.wrap(StockApi.class, plain, retry);

        assertThat(proxied.getStock("42", 0)).isEqualTo("stock-42");
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void dynamicProxyUnwrapsExceptionsAndSkipsClientErrors() {
        server.reset(10, 404);
        StockApi proxied = RetryProxy.wrap(StockApi.class, plain, retry);

        // If the proxy forgot to unwrap InvocationTargetException, this would be an
        // UndeclaredThrowableException and the 404 would not be recognised as non-retryable.
        assertThatThrownBy(() -> proxied.getStock("42", 0)).isInstanceOf(HttpClientErrorException.class);
        assertThat(server.hits()).isEqualTo(1);
    }
}
