package com.example.retrydemo.config;

import com.example.retrydemo.client.StockApi;
import com.example.retrydemo.client.ThirdPartyStockClient;
import com.example.retrydemo.option4.RetryProxy;
import com.example.retrydemo.option4.RetryingStockApi;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * All beans are typed StockApi and selected by name with @Qualifier, to make visible
 * which variant each demo endpoint uses.
 */
@Configuration(proxyBeanMethods = false)
public class ClientsConfig {

    /** Built from the static factory: untouched by any customizer, no retry at all. */
    @Bean
    StockApi plainClient(@Value("${demo.upstream.base-url}") String baseUrl) {
        return new ThirdPartyStockClient(RestClient.builder(), baseUrl);
    }

    /** Built from the auto-configured builder: gets the interceptor from the RestClientCustomizer (option 3). */
    @Bean
    StockApi interceptedClient(RestClient.Builder autoConfiguredBuilder,
                               @Value("${demo.upstream.base-url}") String baseUrl) {
        return new ThirdPartyStockClient(autoConfiguredBuilder, baseUrl);
    }

    /** Option 4a: explicit decorator around the plain client. */
    @Bean
    StockApi decoratedClient(@Qualifier("plainClient") StockApi plain, RetryRegistry registry) {
        return new RetryingStockApi(plain, registry.retry("decorator"));
    }

    /** Option 4b: generic dynamic proxy around the plain client. */
    @Bean
    StockApi proxiedClient(@Qualifier("plainClient") StockApi plain, RetryRegistry registry) {
        return RetryProxy.wrap(StockApi.class, plain, registry.retry("proxy"));
    }
}
