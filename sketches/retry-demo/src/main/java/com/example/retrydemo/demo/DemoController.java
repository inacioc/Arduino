package com.example.retrydemo.demo;

import com.example.retrydemo.client.StockApi;
import com.example.retrydemo.option1.CallSiteRetryService;
import com.example.retrydemo.option2.SpringRetryableService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/**
 * One endpoint per approach. Watch the console while calling them, for example:
 *   curl "localhost:8080/demo/callsite/7?failures=2"   -> recovers after 2 retries
 *   curl "localhost:8080/demo/callsite/7?failures=5"   -> gives up, answers 502
 *   curl "localhost:8080/demo/plain/7?failures=1"      -> no retry at all, answers 502
 */
@RestController
@RequestMapping("/demo")
class DemoController {

    private final StockApi plain;
    private final StockApi intercepted;
    private final StockApi decorated;
    private final StockApi proxied;
    private final CallSiteRetryService callSite;
    private final SpringRetryableService springRetryable;

    DemoController(@Qualifier("plainClient") StockApi plain,
                   @Qualifier("interceptedClient") StockApi intercepted,
                   @Qualifier("decoratedClient") StockApi decorated,
                   @Qualifier("proxiedClient") StockApi proxied,
                   CallSiteRetryService callSite,
                   SpringRetryableService springRetryable) {
        this.plain = plain;
        this.intercepted = intercepted;
        this.decorated = decorated;
        this.proxied = proxied;
        this.callSite = callSite;
        this.springRetryable = springRetryable;
    }

    @GetMapping("/plain/{id}")
    String plain(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return plain.getStock(id, failures);
    }

    @GetMapping("/callsite/{id}")
    String callSite(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return callSite.getStock(id, failures);
    }

    @GetMapping("/spring-retryable/{id}")
    String springRetryable(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return springRetryable.getStock(id, failures);
    }

    @GetMapping("/interceptor/{id}")
    String interceptor(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return intercepted.getStock(id, failures);
    }

    @GetMapping("/decorator/{id}")
    String decorator(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return decorated.getStock(id, failures);
    }

    @GetMapping("/proxy/{id}")
    String proxy(@PathVariable String id, @RequestParam(defaultValue = "2") int failures) {
        return proxied.getStock(id, failures);
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<String> upstreamFailed(RestClientException e) {
        return ResponseEntity.status(502).body("upstream failed: " + e.getMessage());
    }
}
