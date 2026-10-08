package com.example.retrydemo;

import com.example.retrydemo.client.StockApi;
import com.example.retrydemo.option2.SpringRetryableService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full application context: proves that Spring Boot's auto-configured RestClient.Builder
 * really receives the retry interceptor (option 3), that the "plain" client does not,
 * and that Spring Framework 7's @Retryable is active (option 2).
 */
@SpringBootTest(properties = "demo.retry.initial-interval-ms=1")
class SpringWiringTest {

    static final FlakyServer UPSTREAM = startUpstream();

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("demo.upstream.base-url", UPSTREAM::baseUrl);
    }

    @AfterAll
    static void stop() {
        UPSTREAM.close();
    }

    @Autowired
    @Qualifier("interceptedClient")
    StockApi intercepted;

    @Autowired
    @Qualifier("plainClient")
    StockApi plain;

    @Autowired
    SpringRetryableService springRetryable;

    @BeforeEach
    void resetUpstream() {
        UPSTREAM.reset(0, 503);
    }

    @Test
    void customizerInstallsRetryOnAutoConfiguredBuilder() {
        UPSTREAM.reset(2, 503);

        assertThat(intercepted.getStock("42", 0)).isEqualTo("stock-42");
        assertThat(UPSTREAM.hits()).isEqualTo(3);
    }

    @Test
    void plainClientBuiltWithStaticFactoryIsNotAffected() {
        UPSTREAM.reset(1, 503);

        assertThatThrownBy(() -> plain.getStock("42", 0)).isInstanceOf(HttpServerErrorException.class);
        assertThat(UPSTREAM.hits()).isEqualTo(1);
    }

    @Test
    void springFrameworkRetryableAnnotationRetries() {
        UPSTREAM.reset(2, 503);

        assertThat(springRetryable.getStock("42", 0)).isEqualTo("stock-42");
        assertThat(UPSTREAM.hits()).isEqualTo(3); // maxRetries = 2 -> 3 attempts
    }

    private static FlakyServer startUpstream() {
        try {
            return new FlakyServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
