package com.example.sso.appa.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.sso.appa.client.CallOutcome;
import com.example.sso.appa.client.DownstreamCaller;
import com.example.sso.appa.scheduled.MachineToMachineScheduler;

/**
 * Application A's pages.
 *
 * <p>{@code /} is public - it has to be, otherwise you could never see the "not logged in" state.
 * Everything under {@code /ui} requires the {@code app-a-user} realm role.
 */
@Controller
public class HomeController {

    private final MachineToMachineScheduler scheduler;
    private final DownstreamCaller caller;
    private final String appBBaseUrl;

    public HomeController(MachineToMachineScheduler scheduler, DownstreamCaller caller,
            @Value("${lab.app-b.base-url}") String appBBaseUrl) {
        this.scheduler = scheduler;
        this.caller = caller;
        this.appBBaseUrl = appBBaseUrl;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("user", user);
        return "index";
    }

    /** Shows what SSO domain A told us about the person browsing. */
    @GetMapping("/ui/profile")
    public String profile(@AuthenticationPrincipal OidcUser user, Authentication authentication,
            Model model) {
        model.addAttribute("user", user);
        model.addAttribute("appBBaseUrl", appBBaseUrl);
        model.addAttribute("claims", sorted(user.getIdToken().getClaims()));
        // Deliberately the Authentication's authorities, not the OidcUser's. The
        // GrantedAuthoritiesMapper feeds the Authentication, while the principal keeps the raw
        // authorities it was built with - so reading them off the principal shows OIDC_USER and
        // SCOPE_* but none of the ROLE_* the access rules actually use. Confusing to debug: the
        // page claims you have no roles while the very page you are reading required one.
        model.addAttribute("authorities", authentication.getAuthorities().stream()
                .map(Object::toString).sorted().toList());
        return "profile";
    }

    /** What the background jobs have achieved so far, with no user involved. */
    @GetMapping("/ui/machine-calls")
    public String machineCalls(Model model) {
        Map<String, CallOutcome> outcomes = scheduler.lastOutcomes();
        model.addAttribute("appB", outcomes.get("app-b"));
        model.addAttribute("appC", outcomes.get("app-c"));
        return "machine-calls";
    }

    /**
     * The same Application B endpoint the scheduler hits, called with the browsing user's token
     * instead of Application A's own. Compare the two "caller" blocks side by side.
     */
    @GetMapping("/ui/call-app-b-as-me")
    public String callAppBAsMe(Model model) {
        model.addAttribute("relayed", caller.fetchAppBReportsAsCurrentUser());
        model.addAttribute("machine", scheduler.lastOutcomes().get("app-b"));
        return "relay";
    }

    private static List<Map.Entry<String, Object>> sorted(Map<String, Object> claims) {
        return claims.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();
    }
}
