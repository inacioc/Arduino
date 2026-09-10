package com.example.ordermanagement.frontend.client;

import com.example.ordermanagement.frontend.client.exception.BackendConflictException;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendOrderValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that adapter-in-web's ResponseApiError responses - the HTTP shape its
 * GlobalExceptionHandler produces from the domain exceptions - are translated back
 * into the typed exceptions the MVC controllers catch.
 */
class ResponseApiErrorTranslatorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void notFoundBecomesBackendNotFoundException() {
        RestClientResponseException ex = responseOf(HttpStatus.NOT_FOUND, """
                {"status":"NOT_FOUND","errors":[
                  {"code":"ORDER_NOT_FOUND","level":"BLOCKING","label":"Order Not Found",
                   "description":"Order not found","uriDesc":"https://errors.example.com/ORDER_NOT_FOUND",
                   "errorsValueList":[]}
                ]}""");

        RuntimeException translated = ResponseApiErrorTranslator.translate(ex, objectMapper);

        assertThat(translated).isInstanceOf(BackendNotFoundException.class);
        assertThat(translated.getMessage()).isEqualTo("Order not found");
    }

    @Test
    void conflictBecomesBackendConflictException() {
        RestClientResponseException ex = responseOf(HttpStatus.CONFLICT, """
                {"status":"CONFLICT","errors":[
                  {"code":"ILLEGAL_STATE","level":"BLOCKING","label":"Conflict",
                   "description":"Order is not PENDING","uriDesc":"https://errors.example.com/ILLEGAL_STATE",
                   "errorsValueList":[]}
                ]}""");

        RuntimeException translated = ResponseApiErrorTranslator.translate(ex, objectMapper);

        assertThat(translated).isInstanceOf(BackendConflictException.class);
        assertThat(translated.getMessage()).isEqualTo("Order is not PENDING");
    }

    @Test
    void unprocessableContentBecomesBackendOrderValidationExceptionWithLineErrors() {
        UUID productId = UUID.randomUUID();
        String body = """
                {"status":"UNPROCESSABLE_CONTENT","errors":[
                  {"code":"PRODUCT_NOT_FOUND","level":"BLOCKING","label":"Product Not Found",
                   "description":"Product not found","uriDesc":"https://errors.example.com/PRODUCT_NOT_FOUND",
                   "errorsValueList":["%s"]}
                ]}""".formatted(productId);
        RestClientResponseException ex = responseOf(HttpStatus.UNPROCESSABLE_CONTENT, body);

        RuntimeException translated = ResponseApiErrorTranslator.translate(ex, objectMapper);

        assertThat(translated).isInstanceOf(BackendOrderValidationException.class);
        var validationException = (BackendOrderValidationException) translated;
        assertThat(validationException.getErrors()).hasSize(1);
        assertThat(validationException.getErrors().get(0).productId()).isEqualTo(productId);
        assertThat(validationException.getErrors().get(0).code()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    private RestClientResponseException responseOf(HttpStatus status, String jsonBody) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                jsonBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
