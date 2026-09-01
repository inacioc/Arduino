package com.example.sso.appb.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.sso.appb.domain.ReportRepository;

/**
 * Application B's browser pages. Handled by the second filter chain, so these use a session cookie
 * and {@code oauth2Login}, not bearer tokens.
 */
@Controller
public class ReportUiController {

    private final ReportRepository reports;

    public ReportUiController(ReportRepository reports) {
        this.reports = reports;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("user", user);
        return "index";
    }

    /**
     * Requires the {@code app-b-user} realm role. Arriving here straight from Application A without
     * a password prompt is the SSO demonstration; being bounced with a 403 as {@code bob} is the
     * authorization demonstration.
     */
    @GetMapping("/ui/reports")
    public String reports(@AuthenticationPrincipal OidcUser user, Authentication authentication,
            Model model) {
        model.addAttribute("user", user);
        model.addAttribute("reports", this.reports.findAll());
        // The Authentication's authorities, not the principal's: the GrantedAuthoritiesMapper feeds
        // the former. Reading the principal's would show SCOPE_* but no ROLE_*, which looks alarming
        // on a page that required ROLE_app-b-user to reach.
        model.addAttribute("authorities", authentication.getAuthorities().stream()
                .map(Object::toString).sorted().toList());
        return "reports";
    }
}
