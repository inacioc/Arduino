package com.example.sso.appc.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sso.appc.domain.InventoryItem;
import com.example.sso.appc.domain.InventoryItemRepository;

/**
 * Application C's protected API - the target of Application A's <em>cross-domain</em> scheduled call.
 *
 * <p>The response shape matches Application B's on purpose, so the only interesting difference when
 * comparing the two results is the {@code issuer} and the client id.
 */
@RestController
public class InventoryApiController {

    private static final Logger log = LoggerFactory.getLogger(InventoryApiController.class);

    private final InventoryItemRepository inventory;

    public InventoryApiController(InventoryItemRepository inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/api/inventory")
    public ResourceResponse inventory(@AuthenticationPrincipal Jwt jwt) {
        CallerIdentity caller = CallerIdentity.of(jwt);
        log.info("GET /api/inventory | caller={} | serviceAccount={} | client={} | issuer={}",
                caller.username(), caller.serviceAccount(), caller.clientId(), caller.issuer());

        List<Map<String, Object>> items = this.inventory.findAll().stream()
                .map(InventoryApiController::toMap)
                .toList();
        return new ResourceResponse("app-c", "inventory", caller, items);
    }

    @GetMapping("/api/whoami")
    public CallerIdentity whoami(@AuthenticationPrincipal Jwt jwt) {
        return CallerIdentity.of(jwt);
    }

    private static Map<String, Object> toMap(InventoryItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("sku", item.getSku());
        map.put("description", item.getDescription());
        map.put("quantity", item.getQuantity());
        return map;
    }
}
