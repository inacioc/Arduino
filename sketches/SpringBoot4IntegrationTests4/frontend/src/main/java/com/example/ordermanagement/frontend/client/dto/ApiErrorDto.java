package com.example.ordermanagement.frontend.client.dto;

import java.util.List;

/**
 * Mirrors adapter-in-web's {@code ApiError} response shape. Duplicated here rather
 * than shared as a dependency: the frontend module deliberately depends on none of
 * the hexagon's modules (see the root pom's module comment) - it only ever talks
 * HTTP+JSON to adapter-in-web's REST API, the same way any other external client
 * would, so it carries its own minimal copy of the wire shape it needs.
 */
public record ApiErrorDto(
        String code,
        String level,
        String label,
        String description,
        String uriDesc,
        List<String> errorsValueList
) {}
