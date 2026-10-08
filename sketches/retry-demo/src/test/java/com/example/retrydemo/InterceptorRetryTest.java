package com.example.retrydemo;

import com.example.retrydemo.client.StockApi;
import com.example.retrydemo.client.ThirdPartyStockClient;
import com.example.retrydemo.config.ResilienceConfig;
import com.example.retrydemo.option3.RetryingClientHttpRequestInterceptor;
import io.github.resilience4j.retry.Retry;
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

/**
 * Option 3: the retry lives in a RestClient interceptor. The "SDK" only receives a builder,
 * we never touch the built RestClient.
 */
class InterceptorRetryTest {

    static FlakyServer server;
    RestClient.Builder builder;
    StockApi client;

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
        Retry retry = Retry.of("interceptor-test", ResilienceConfig.retryConfig(Duration.ofMillis(1)));
        builder = RestClient.builder().requestInterceptor(new RetryingClientHttpRequestInterceptor(retry));
        client = new ThirdPartyStockClient(builder, server.baseUrl());
    }

    @Test
    void retriesTransient503ThenSucceeds() {
        server.reset(2, 503);

        assertThat(client.getStock("42", 0)).isEqualTo("stock-42");
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void lastFailingResponseSurfacesAsTheUsualRestClientException() {
        server.reset(10, 503);

        assertThatThrownBy(() -> client.getStock("42", 0)).isInstanceOf(HttpServerErrorException.class);
        assertThat(server.hits()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        server.reset(10, 404);

        assertThatThrownBy(() -> client.getStock("42", 0)).isInstanceOf(HttpClientErrorException.class);
        assertThat(server.hits()).isEqualTo(1);
    }

    @Test
    void doesNotRetryNonIdempotentPost() {
        server.reset(10, 503);
        RestClient restClient = builder.baseUrl(server.baseUrl()).build();

        assertThatThrownBy(() -> restClient.post().uri("/orders").retrieve().toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.class);
        assertThat(server.hits()).isEqualTo(1); // retrying a POST could create duplicates
    }
}
