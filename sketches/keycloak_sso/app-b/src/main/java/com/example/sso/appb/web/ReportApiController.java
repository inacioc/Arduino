package com.example.sso.appb.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sso.appb.domain.Report;
import com.example.sso.appb.domain.ReportRepository;

/**
 * Application B's protected API - the target of Application A's scheduled machine-to-machine call
 * and of the token-relay call.
 *
 * <p>Reached through the {@code /api/**} filter chain, so by the time a method here runs the bearer
 * token has already been validated: signature against domain A's JWKS, {@code iss}, {@code exp}
 * and {@code aud} all checked, and realm roles turned into authorities.
 */
@RestController
public class ReportApiController {

    private static final Logger log = LoggerFactory.getLogger(ReportApiController.class);

    private final ReportRepository reports;

    public ReportApiController(ReportRepository reports) {
        this.reports = reports;
    }

    /**
     * Returns the reports plus a description of who asked for them. The response includes the
     * caller identity so the same endpoint can show, without any guesswork, whether it was reached
     * by an application or by a human.
     */
    @GetMapping("/api/reports")
    public ResourceResponse reports(@AuthenticationPrincipal Jwt jwt) {
        CallerIdentity caller = CallerIdentity.of(jwt);
        log.info("GET /api/reports | caller={} | serviceAccount={} | client={} | roles={}",
                caller.username(), caller.serviceAccount(), caller.clientId(), caller.roles());

        List<Map<String, Object>> items = this.reports.findAll().stream()
                .map(ReportApiController::toMap)
                .toList();
        return new ResourceResponse("app-b", "reports", caller, items);
    }

    /**
     * Identity only, no payload. Handy for {@code curl} when you just want to decode what a token
     * grants you.
     */
    @GetMapping("/api/whoami")
    public CallerIdentity whoami(@AuthenticationPrincipal Jwt jwt) {
        return CallerIdentity.of(jwt);
    }

    /** LinkedHashMap, not Map.of: the column order in the rendered table should be stable. */
    private static Map<String, Object> toMap(Report report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", report.getId());
        map.put("title", report.getTitle());
        map.put("owner", report.getOwner());
        map.put("status", report.getStatus());
        return map;
    }
}
