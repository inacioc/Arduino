package com.example.sso.appb.web;

import java.util.List;
import java.util.Map;

/**
 * Response envelope shared by Application B and Application C, so Application A can read either
 * with one record.
 */
public record ResourceResponse(
        String application,
        String resource,
        CallerIdentity caller,
        List<Map<String, Object>> items) {
}
