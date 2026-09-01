# Verification record

What was actually executed on this machine while building the lab, and the output it produced. Kept
so you can tell the difference between "this should work" and "this was run".

Environment: Windows 11, Temurin JDK 25.0.3, Maven 3.9.1, Keycloak 26.7.0, Spring Boot 4.0.7
(Spring Framework 7.0.8, Spring Security 7.0.6).

---

## 1. Build

```
mvn clean install
```

All three modules compile and package on Java 25.

## 2. Both SSO domains provisioned from the realm JSON files

```
.\scripts\verify-keycloak.ps1

--- SSO domain A (Application A + Application B) ---
  discovery    OK
  issuer       http://localhost:8080/realms/sso-domain-a
  token url    http://localhost:8080/realms/sso-domain-a/protocol/openid-connect/token
  jwks url     http://localhost:8080/realms/sso-domain-a/protocol/openid-connect/certs
  client       app-a-m2m : token issued OK

--- SSO domain C (Application C) ---
  discovery    OK
  issuer       http://localhost:8090/realms/sso-domain-c
  token url    http://localhost:8090/realms/sso-domain-c/protocol/openid-connect/token
  jwks url     http://localhost:8090/realms/sso-domain-c/protocol/openid-connect/certs
  client       app-a-federated-m2m : token issued OK
```

Both instances ran simultaneously — domain A on 8080/9000, domain C on 8090/9090.

## 3. Token-level checks — 8/8

```
.\scripts\get-token.ps1

  [PASS] GET http://localhost:8082/api/reports with app-a-m2m token -> HTTP 200 (expected 200)
  [PASS] GET http://localhost:8083/api/inventory with domain C token -> HTTP 200 (expected 200)
  [PASS] GET http://localhost:8082/api/reports as alice -> HTTP 200 (expected 200)
  [PASS] GET http://localhost:8082/api/reports as bob -> HTTP 403 (expected 403)
  [PASS] GET http://localhost:8082/api/reports with no token -> HTTP 401 (expected 401)
  [PASS] GET http://localhost:8083/api/inventory with no token -> HTTP 401 (expected 401)
  [PASS] GET http://localhost:8083/api/inventory with a DOMAIN A token -> HTTP 401 (expected 401)
  [PASS] GET http://localhost:8083/api/inventory with a DOMAIN C token (frank) -> HTTP 200 (expected 200)

All 8 checks passed.
```

The machine token was confirmed to carry the mappers the realm files configure:

```
--- decoded token: app-a-m2m from domain A ---
  iss                : http://localhost:8080/realms/sso-domain-a
  aud                : app-b-api                       <- audience mapper works
  azp                : app-a-m2m
  preferred_username : service-account-app-a-m2m
  realm roles        : app-b-api-reader                <- service-account role assignment works
```

## 4. Scheduled machine-to-machine calls (Application A's log)

```
Started AppAApplication in 2.122 seconds
[scheduled -> app-b] OK  | app-b accepted us as application 'app-a-m2m' (service account);
                           issuer=http://localhost:8080/realms/sso-domain-a; roles=[app-b-api-reader]; 4 item(s)
[scheduled -> app-c] OK  | app-c accepted us as application 'app-a-federated-m2m' (service account);
                           issuer=http://localhost:8090/realms/sso-domain-c; roles=[app-c-api-reader]; 4 item(s)
```

No `servletRequest cannot be null` — the `AuthorizedClientServiceOAuth2AuthorizedClientManager`
does its job on the scheduler thread.

## 5. Browser SSO, driven end to end

The `authorization_code` flow was completed against the real login form (cookie jar, form POST), not
simulated.

```
GET  /                          -> 200   (public page)
GET  /ui/profile                -> 302   /oauth2/authorization/keycloak-a
     -> Keycloak /auth?client_id=app-a-web&code_challenge_method=S256   (redirect URI accepted, PKCE on)
POST login form as alice        -> 200   http://localhost:8081/ui/profile
```

Authorities rendered on the profile page:

```
OIDC_USER, ROLE_app-a-user, ROLE_app-b-user, SCOPE_email, SCOPE_openid, SCOPE_profile
```

Then, in the same browser session, Application B:

```
GET  http://localhost:8082/ui/reports  -> 200
     no password form in the response  -> SSO confirmed
     page contains: alice, ROLE_app-b-user, "Quarterly revenue", "Churn analysis"
```

And the authorization case:

```
POST login form as bob                 -> 200  http://localhost:8081/ui/profile
GET  http://localhost:8082/ui/reports  -> 403  (no password prompt: SSO happened, authorization refused)
```

## 6. Token relay — same endpoint, two identities

`GET /ui/call-app-b-as-me` rendered both cards as SUCCESS. Application B's own account of who called
it:

| | relayed (user) | scheduled (machine) |
|---|---|---|
| `iss` | `…:8080/realms/sso-domain-a` | `…:8080/realms/sso-domain-a` |
| `azp` | `app-a-web` | `app-a-m2m` |
| `sub` | `402c2026-570c-…` | `8f081a7d-905b-…` |
| `preferred_username` | `alice` | `service-account-app-a-m2m` |
| `aud` | `[app-b-api]` | `[app-b-api]` |
| realm roles | `[app-a-user, app-b-user]` | `[app-b-api-reader]` |
| service account? | no — a human is behind this call | yes — an application called us |

---

## Problems found and fixed during verification

Recorded because each one is a trap you could hit while modifying the lab. All are covered in
[troubleshooting.md](troubleshooting.md).

| Symptom | Cause | Fix |
|---|---|---|
| Realm import aborted, server refused to start | `serviceAccountClientLink` is not a valid field on `UserRepresentation` | use **`serviceAccountClientId`** |
| `Table "REPORT" not found` at startup | `data.sql` ran before Hibernate created the schema | `spring.jpa.defer-datasource-initialization: true` in B and C |
| Profile page listed no `ROLE_*`, yet the page required one to reach | read `OidcUser.getAuthorities()` (principal, unmapped) instead of `Authentication.getAuthorities()` (mapped) | both UI controllers now read the `Authentication` |
| `install-keycloak.ps1` aborted on a line that had succeeded | PowerShell 5.1 turns a native command's `2>&1` stderr into an error record, and `$ErrorActionPreference='Stop'` aborts | use `java --version`, which writes to stdout |

## Not verified automatically

- **RP-initiated single logout** is written but commented out, since it was not requested. The
  one-line change is marked in `app-a/…/SecurityConfig.java`.
- **The `-Fresh` / `reset-keycloak.ps1` paths** were exercised by hand (the realm databases were
  wiped and re-imported during the `serviceAccountClientId` fix), but there is no automated check.
- **No unit or integration tests** ship with the lab; `scripts\get-token.ps1` is the regression check.
- **The Bruno collection was not executed.** It now exists as real files in
  [../bruno/keycloak-sso-lab/](../bruno/keycloak-sso-lab/) — 15 files, documented in
  [../bruno.md](../bruno.md) — and passes static checks: balanced blocks, a well-formed `meta` block
  in every request, exactly one method block per request whose `auth:` line agrees with the presence
  of an `auth:oauth2` block, every `{{var}}` defined in the environment, and a distinct
  `credentials_id` per domain-and-client so no request can reuse a cached token from the wrong SSO
  domain. But **no request has been run against the live lab**, unlike everything else on this page.
  The expected results it asserts are the ones recorded above.

## Change made after this run

`app-a-web` in `keycloak/domain-a/realm-sso-domain-a.json` gained a second redirect URI,
`http://localhost:9876/callback`, so the Bruno collection can drive the authorization-code flow.
Purely additive: it grants the applications no new capability and invalidates nothing recorded
above. The realm imported at `C:\Apps\keycloak-a` during the run above predates it — `--import-realm`
skips existing realms, so `scripts\reset-keycloak.ps1` (or an admin-console edit) is needed before
that URI is live.
