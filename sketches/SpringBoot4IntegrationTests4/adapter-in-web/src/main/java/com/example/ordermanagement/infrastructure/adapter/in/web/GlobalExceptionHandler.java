package com.example.ordermanagement.infrastructure.adapter.in.web;

import com.example.ordermanagement.domain.exception.DomainException;
import com.example.ordermanagement.domain.exception.DomainValidationException;
import com.example.ordermanagement.domain.exception.InfrastructureUnavailableException;
import com.example.ordermanagement.domain.exception.OptimisticLockingConflictException;
import com.example.ordermanagement.domain.exception.PersistenceDataValidationException;
import com.example.ordermanagement.domain.exception.ResourceAlreadyExistsException;
import com.example.ordermanagement.domain.exception.ResourceNotFoundException;
import com.example.ordermanagement.infrastructure.adapter.in.web.error.ApiError;
import com.example.ordermanagement.infrastructure.adapter.in.web.error.ErrorDocs;
import com.example.ordermanagement.infrastructure.adapter.in.web.error.ErrorLevel;
import com.example.ordermanagement.infrastructure.adapter.in.web.error.ResponseApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Translates every exception this API's controllers (or anything they call — domain
 * services, driven ports) can throw into the same {@link ResponseApiError} envelope,
 * so a client only ever has to parse one response shape regardless of what went wrong.
 * <p>
 * Handlers below are grouped by <b>where the failure came from</b>, which is also how
 * {@code Validation.md} explains the overall strategy:
 * <ol>
 *   <li><b>Domain / functional errors</b> — {@code domain.exception.*}: a use case
 *       decided the request can't succeed (not found, already exists, notification-pattern
 *       {@code DomainValidator} rule sets) plus the two JDK exceptions aggregates use for their own
 *       invariants per this project's convention ({@link IllegalArgumentException}/
 *       {@link IllegalStateException} — see {@code DomainException}'s javadoc).</li>
 *   <li><b>Infrastructure errors</b> — also {@code domain.exception.*}, but the technical
 *       subtypes {@code AbstractPersistenceAdapter}/{@code OrderMqPublisher} translate
 *       {@code DataAccessException}/{@code JmsException} into: a database/broker that's
 *       unavailable, a constraint violation that isn't a simple business duplicate, a lost
 *       concurrent-update race.</li>
 *   <li><b>Request-shape errors</b> — Bean Validation and the usual Spring MVC
 *       binding/routing exceptions: the request never made it to domain code at all.</li>
 *   <li><b>Security</b> — {@code AccessDeniedException} from {@code @PreAuthorize}
 *       denials specifically; see the javadoc on that handler for why 401s and
 *       filter-level 403s are handled elsewhere ({@code RestAuthenticationEntryPoint},
 *       {@code RestAccessDeniedHandler}) instead of here.</li>
 *   <li><b>Fallback</b> — anything not covered above.</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorDocs errorDocs;

    public GlobalExceptionHandler(ErrorDocs errorDocs) {
        this.errorDocs = errorDocs;
    }

    // ── 1. Domain / functional errors ───────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResponseApiError> handleNotFound(ResourceNotFoundException ex) {
        String code = codeFor(ex.getResourceName(), "NOT_FOUND");
        return respond(HttpStatus.NOT_FOUND,
                singleError(code, ErrorLevel.BLOCKING, humanize(code), ex.getMessage()));
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ResponseApiError> handleAlreadyExists(ResourceAlreadyExistsException ex) {
        String code = codeFor(ex.getResourceName(), "ALREADY_EXISTS");
        return respond(HttpStatus.CONFLICT,
                singleError(code, ErrorLevel.BLOCKING, humanize(code), ex.getMessage()));
    }

    /** Every violation collected by the domain's {@code DomainValidator}s, grouped by rule code. */
    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ResponseApiError> handleDomainValidation(DomainValidationException ex) {
        List<FieldProblem> problems = ex.getViolations().stream()
                .map(v -> new FieldProblem(v.propertyPath(), v.code(), v.message()))
                .toList();
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, groupByCode(problems, ErrorLevel.BLOCKING));
    }

    /**
     * An aggregate invariant was violated while constructing/mutating it (e.g.
     * {@code Order.create} with no items, {@code Product.create} with a non-positive
     * price). Per this project's convention, aggregates throw the plain JDK exception
     * rather than a {@code DomainException} subtype — see {@code DomainException}'s javadoc.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.BAD_REQUEST,
                singleError("INVALID_REQUEST", ErrorLevel.BLOCKING, "Invalid Request", ex.getMessage()));
    }

    /** An aggregate rejected an illegal state transition (e.g. confirming an already-confirmed order). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseApiError> handleIllegalState(IllegalStateException ex) {
        return respond(HttpStatus.CONFLICT,
                singleError("ILLEGAL_STATE", ErrorLevel.BLOCKING, "Conflict", ex.getMessage()));
    }

    // ── 2. Infrastructure errors ─────────────────────────────────────────────────
    //
    // All three below are ErrorLevel.FROCED: none of them are the caller's fault, and
    // ex.getMessage() is safe to return as-is for each - AbstractPersistenceAdapter and
    // OrderMqPublisher are deliberately written to never put driver/SQL/broker detail
    // into the message they attach.

    @ExceptionHandler(PersistenceDataValidationException.class)
    public ResponseEntity<ResponseApiError> handleConstraintViolation(PersistenceDataValidationException ex) {
        log.warn("Data constraint violation reached the web layer: {}", ex.getMessage());
        return respond(HttpStatus.CONFLICT,
                singleError("DATA_CONSTRAINT_VIOLATION", ErrorLevel.FROCED, "Data Conflict", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingConflictException.class)
    public ResponseEntity<ResponseApiError> handleOptimisticLock(OptimisticLockingConflictException ex) {
        return respond(HttpStatus.CONFLICT, singleError("OPTIMISTIC_LOCK_CONFLICT", ErrorLevel.FROCED,
                "Concurrent Update Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InfrastructureUnavailableException.class)
    public ResponseEntity<ResponseApiError> handleInfrastructureUnavailable(InfrastructureUnavailableException ex) {
        // Full detail (stack trace, driver/broker specifics) was already logged where the
        // adapter caught it - see AbstractPersistenceAdapter and OrderMqPublisher.
        log.warn("Returning 503: {}", ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE,
                singleError("INFRASTRUCTURE_UNAVAILABLE", ErrorLevel.FROCED, "Service Unavailable", ex.getMessage()));
    }

    // ── 3. Request-shape errors (Bean Validation, MVC binding/routing) ─────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldProblem> problems = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(fe -> new FieldProblem(fe.getField(), fieldErrorCode(fe), fe.getDefaultMessage())),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(ge -> new FieldProblem(null, ge.getCode() != null ? ge.getCode() : "INVALID",
                                        ge.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, groupByCode(problems, ErrorLevel.BLOCKING));
    }

    /** {@code @Validated} on {@code @RequestParam}/{@code @PathVariable} (none in this API today, but a client library upgrade or a new endpoint could add one). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldProblem> problems = ex.getConstraintViolations().stream()
                .map(this::toFieldProblem)
                .toList();
        return respond(HttpStatus.BAD_REQUEST, groupByCode(problems, ErrorLevel.BLOCKING));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseApiError> handleMalformedBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, singleError("MALFORMED_REQUEST_BODY", ErrorLevel.BLOCKING,
                "Malformed Request Body", "The request body could not be parsed. Check for missing/invalid JSON or an invalid field value."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "a different type";
        return respond(HttpStatus.BAD_REQUEST, ApiError.builder()
                .code("TYPE_MISMATCH")
                .level(ErrorLevel.BLOCKING)
                .label("Invalid Parameter")
                .description("'" + ex.getName() + "' must be " + required + ".")
                .uriDesc(errorDocs.uriFor("TYPE_MISMATCH"))
                .errorsValueList(List.of(ex.getName()))
                .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiError.builder()
                .code("MISSING_PARAMETER")
                .level(ErrorLevel.BLOCKING)
                .label("Missing Parameter")
                .description(ex.getMessage())
                .uriDesc(errorDocs.uriFor("MISSING_PARAMETER"))
                .errorsValueList(List.of(ex.getParameterName()))
                .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                singleError("METHOD_NOT_ALLOWED", ErrorLevel.BLOCKING, "Method Not Allowed", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseApiError> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND,
                singleError("RESOURCE_NOT_FOUND", ErrorLevel.BLOCKING, "Resource Not Found", "No endpoint matches this request."));
    }

    // ── 4. Security ──────────────────────────────────────────────────────────
    //
    // AccessDeniedException from a @PreAuthorize denial is thrown from inside
    // DispatcherServlet's handler invocation, so it reaches this @RestControllerAdvice
    // like any other controller exception. AuthenticationException practically never
    // will (an unauthenticated request is rejected by the security filter chain before
    // DispatcherServlet runs) - it's kept here only as a defensive fallback for a
    // controller that checks authentication itself. Both mirror the body shape
    // RestAuthenticationEntryPoint/RestAccessDeniedHandler produce for the same failure
    // caught at the filter-chain level, so the response is identical either way.

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseApiError> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, singleError("ACCESS_DENIED", ErrorLevel.BLOCKING,
                "Access Denied", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseApiError> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, singleError("UNAUTHENTICATED", ErrorLevel.BLOCKING,
                "Authentication Required", "A valid bearer token is required to access this resource."));
    }

    // ── 5. Fallback ──────────────────────────────────────────────────────────

    /** Safety net for a future {@code DomainException} subtype added without updating this class. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ResponseApiError> handleUnclassifiedDomainException(DomainException ex) {
        log.warn("Unclassified domain exception reached the web layer", ex);
        return respond(HttpStatus.BAD_REQUEST,
                singleError("REQUEST_REJECTED", ErrorLevel.BLOCKING, "Request Rejected", ex.getMessage()));
    }

    /**
     * Absolute last resort — a bug, not an expected outcome. Full detail is logged
     * server-side only; the client gets a generic message plus a reference id it can
     * quote in a support request, never the raw exception message or a stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApiError> handleUnexpected(Exception ex) {
        String reference = UUID.randomUUID().toString();
        log.error("Unexpected error [{}]", reference, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, singleError("INTERNAL_ERROR", ErrorLevel.FROCED,
                "Internal Error", "An unexpected error occurred. Reference: " + reference));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record FieldProblem(String field, String code, String message) {}

    /** {@code codeFor("Product", "NOT_FOUND")} → {@code "PRODUCT_NOT_FOUND"}. */
    private static String codeFor(String resourceName, String suffix) {
        return resourceName.toUpperCase(Locale.ROOT).replace(' ', '_') + "_" + suffix;
    }

    private ApiError singleError(String code, ErrorLevel level, String label, String description) {
        return ApiError.builder()
                .code(code)
                .level(level)
                .label(label)
                .description(description)
                .uriDesc(errorDocs.uriFor(code))
                .build();
    }

    private ResponseEntity<ResponseApiError> respond(HttpStatus status, ApiError error) {
        return respond(status, List.of(error));
    }

    private ResponseEntity<ResponseApiError> respond(HttpStatus status, List<ApiError> errors) {
        return ResponseEntity.status(status).body(ResponseApiError.builder().status(status).errors(errors).build());
    }

    private FieldProblem toFieldProblem(ConstraintViolation<?> violation) {
        String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return new FieldProblem(violation.getPropertyPath().toString(), code, violation.getMessage());
    }

    /**
     * {@link FieldError#getCode()} returns the least-specific of Spring's generated
     * message codes — for a {@code @NotBlank} failure, that's the constraint's own
     * simple name ("NotBlank"), which is exactly the stable, machine-readable
     * identifier {@link ApiError#getCode()} needs. Falls back to "INVALID" for a
     * custom {@code Errors.rejectValue(...)} call that didn't supply one.
     */
    private static String fieldErrorCode(FieldError fe) {
        return fe.getCode() != null ? fe.getCode() : "INVALID";
    }

    /**
     * Groups same-code problems into one {@link ApiError} each, collecting every
     * affected field/line into {@link ApiError#getErrorsValueList()} — see that
     * field's javadoc for why (so a client doesn't have to aggregate by code itself).
     */
    private List<ApiError> groupByCode(List<FieldProblem> problems, ErrorLevel level) {
        Map<String, List<FieldProblem>> byCode = problems.stream()
                .collect(Collectors.groupingBy(FieldProblem::code, LinkedHashMap::new, Collectors.toList()));

        return byCode.entrySet().stream()
                .map(entry -> ApiError.builder()
                        .code(entry.getKey())
                        .level(level)
                        .label(humanize(entry.getKey()))
                        .description(entry.getValue().stream()
                                .map(FieldProblem::message)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.joining("; ")))
                        .uriDesc(errorDocs.uriFor(entry.getKey()))
                        .errorsValueList(entry.getValue().stream()
                                .map(FieldProblem::field)
                                .filter(Objects::nonNull)
                                .toList())
                        .build())
                .toList();
    }

    /** {@code "PRODUCT_NOT_FOUND"} / {@code "NotBlank"} → {@code "Product Not Found"} / {@code "Not Blank"}. */
    private static String humanize(String code) {
        if (code == null || code.isBlank()) {
            return "Error";
        }
        String spaced = code.replace('_', ' ').replace('-', ' ')
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        StringBuilder result = new StringBuilder();
        for (String word : spaced.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return !result.isEmpty() ? result.toString() : "Error";
    }
}
