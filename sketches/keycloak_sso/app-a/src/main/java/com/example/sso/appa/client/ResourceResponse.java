package com.example.sso.appa.client;

import java.util.List;
import java.util.Map;

/**
 * Common response shape returned by both Application B and Application C, so a single record can
 * read either one.
 *
 * @param application which application answered
 * @param resource    what the payload is ("reports" / "inventory")
 * @param caller      the identity the callee extracted from our access token
 * @param items       the actual business data, kept loosely typed since it differs per application
 */
public record ResourceResponse(
        String application,
        String resource,
        CallerIdentity caller,
        List<Map<String, Object>> items) {
}
