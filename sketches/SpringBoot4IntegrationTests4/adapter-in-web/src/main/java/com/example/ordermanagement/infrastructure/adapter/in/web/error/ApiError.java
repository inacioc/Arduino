package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One error reported inside a {@link ResponseApiError}. Roughly RFC 7807
 * ("Problem Details for HTTP APIs") in shape, with the field names this
 * project has standardized on and one addition ({@link #errorsValueList}) RFC
 * 7807 doesn't have:
 *
 * <table>
 *   <caption>Field semantics</caption>
 *   <tr><td>{@link #code}</td><td>Stable, machine-readable identifier a client can
 *       branch on without parsing prose — e.g. {@code "PRODUCT_NOT_FOUND"}. Never
 *       changes wording; safe to hardcode in client code. RFC 7807's closest
 *       analogue is {@code type}, but that's a URI — this is the short form of it.</td></tr>
 *   <tr><td>{@link #level}</td><td>See {@link ErrorLevel}.</td></tr>
 *   <tr><td>{@link #label}</td><td>Short, human-readable title — RFC 7807's {@code title}.
 *       Fixed per {@code code} (e.g. {@code "PRODUCT_NOT_FOUND"} always labels
 *       {@code "Product Not Found"}); safe to show as a heading.</td></tr>
 *   <tr><td>{@link #description}</td><td>The specific, request-scoped detail — RFC 7807's
 *       {@code detail}. May embed data from this request (an id, a submitted value);
 *       never a raw exception message or stack trace for a {@code FROCED}-level
 *       error — see {@code Validation.md} on why those descriptions are deliberately generic.</td></tr>
 *   <tr><td>{@link #uriDesc}</td><td>A dereferenceable link to this error code's
 *       documentation — RFC 7807's {@code type}, kept as a distinct field
 *       (rather than folded into {@code code}) so the machine-readable code and the
 *       human-followable link can evolve independently.</td></tr>
 *   <tr><td>{@link #errorsValueList}</td><td>Present only for field/line-level errors
 *       (Bean Validation, the domain's Notification-pattern order validation): every
 *       field path or line identifier this {@code code} applies to, e.g. two order
 *       lines that both failed with {@code PRODUCT_NOT_AVAILABLE} appear as one
 *       {@code ApiError} with two entries here rather than two separate
 *       {@code ApiError}s — grouped this way so a client doesn't have to aggregate by
 *       code itself to answer "which of my fields have a PRODUCT_NOT_AVAILABLE
 *       problem?". Empty (never null) when the error isn't tied to specific fields
 *       (not-found, conflict, technical).</td></tr>
 * </table>
 */
@Data
@Builder
public class ApiError {

    private final String code;
    private final ErrorLevel level;
    private final String label;
    private final String description;
    private final String uriDesc;

    @Builder.Default
    private final List<String> errorsValueList = List.of();
}
