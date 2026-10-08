package com.example.retrydemo.option3;

import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OPTION 3: retry inside a RestClient interceptor, installed through a RestClientCustomizer
 * (see ResilienceConfig). Use it when you cannot touch the client instance but the code that
 * creates it takes the auto-configured RestClient.Builder.
 *
 * Things this class gets right on purpose:
 *  - only idempotent methods are retried (a retried POST can create duplicates);
 *  - a 5xx response is closed before the next attempt, so connections are not leaked;
 *  - after the last attempt the last response is returned, so callers still get the usual
 *    HttpServerErrorException from RestClient's status handling.
 *
 * Caveat: the ClientHttpRequestExecution passed to intercept() walks the remaining interceptors
 * of the chain. Re-invoking it is only fully safe when this interceptor is the last one
 * registered. Verify this against your Spring version if you stack several interceptors.
 */
public class RetryingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<HttpMethod> IDEMPOTENT = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS);

    private final Retry retry;

    public RetryingClientHttpRequestInterceptor(Retry retry) {
        this.retry = retry;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        if (!IDEMPOTENT.contains(request.getMethod())) {
            return execution.execute(request, body);
        }

        AtomicReference<ClientHttpResponse> lastFailed = new AtomicReference<>();
        try {
            return retry.executeCheckedSupplier(() -> {
                closeQuietly(lastFailed.getAndSet(null)); // release the previous attempt's connection
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().is5xxServerError()) {
                    lastFailed.set(response);
                    throw new RetryableStatusException(response);
                }
                return response;
            });
        } catch (RetryableStatusException exhausted) {
            return exhausted.getResponse(); // attempts used up: let RestClient raise its normal error
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            if (t instanceof Error error) {
                throw error;
            }
            throw new IOException(t);
        }
    }

    private static void closeQuietly(ClientHttpResponse response) {
        if (response != null) {
            response.close();
        }
    }
}
