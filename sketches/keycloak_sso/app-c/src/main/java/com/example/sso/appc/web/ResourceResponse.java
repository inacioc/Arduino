package com.example.sso.appc.web;

import java.util.List;
import java.util.Map;

/** Same envelope Application B uses, so Application A reads both with one record. */
public record ResourceResponse(
        String application,
        String resource,
        CallerIdentity caller,
        List<Map<String, Object>> items) {
}
