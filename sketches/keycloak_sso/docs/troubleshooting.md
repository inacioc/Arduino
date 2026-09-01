# Troubleshooting

Ordered by how often each one bites. Every entry below is a failure that was either hit while
building this lab or is a direct consequence of its configuration.

## First: narrow it down

**401 vs 403 tells you which half is broken.**

- **401 Unauthorized** — the token was missing, expired, signed by the wrong issuer, or aimed at the
  wrong audience. The caller is not *authenticated*. Look at the token and at the resource server's
  `issuer-uri` / `audiences`.
- **403 Forbidden** — the token was fine. The caller is authenticated but lacks the required role.
  Look at role assignments and at the claim-to-authority mapping.

Then check the token itself, which removes most guesswork:

```powershell
.\scripts\get-token.ps1 -Scenario decode -User alice
```

---

## Keycloak

### The second instance will not start

> `Port 9000 already bound` (or a Quarkus startup failure that never mentions 9000)

Keycloak 26 always binds an HTTP **management** interface for health and metrics, default port 9000,
separate from `--http-port`. Two instances on one machine collide there even with different HTTP
ports. Domain C therefore passes `--http-management-port=9090`; it is mandatory, not cosmetic.

### I edited a realm JSON and nothing changed

`start-dev --import-realm` **skips realms that already exist**, and does so silently — the server
starts fine and keeps serving the old configuration. This is the single most confusing failure mode
in this lab.

```powershell
# stop both Keycloak windows (Ctrl+C) first
.\scripts\reset-keycloak.ps1
```

Or per instance: `.\keycloak\domain-a\start-keycloak-a.ps1 -Fresh`.

### Realm import fails with "Unrecognized field ..."

The import is strict: an unknown property aborts the whole import and the server refuses to start.
Helpfully, the error lists every property the class *does* accept — read that list rather than
guessing. This happened here with `serviceAccountClientLink`, which does not exist; the correct field
for linking a service-account user to its client is **`serviceAccountClientId`**.

### `KEYCLOAK_ADMIN` is ignored

Renamed in Keycloak 26 to **`KC_BOOTSTRAP_ADMIN_USERNAME`** / **`KC_BOOTSTRAP_ADMIN_PASSWORD`**.
Older guides (including the ones next to this folder) still show the old names. They only take effect
on the very first start, when no admin user exists yet.

---

## Login and redirects

### `Invalid parameter: redirect_uri`

Keycloak requires an exact match against the client's registered `redirectUris`. Spring builds the
callback from the **registration id**, not the client id:

```
spring.security.oauth2.client.registration.keycloak-a  ->  /login/oauth2/code/keycloak-a
```

So renaming the registration key in `application.yml` silently changes the callback URL. The realm
files register `http://localhost:8081/login/oauth2/code/keycloak-a` and
`http://localhost:8082/login/oauth2/code/keycloak-a`. Watch for `127.0.0.1` vs `localhost` and a
trailing slash — both count as different URIs.

### Endless redirect loop between the app and Keycloak

Usually the login succeeds and then the post-login page is itself protected by a rule the user cannot
satisfy, so Spring bounces them back to authenticate. Confirm by requesting a *public* page. In this
lab `/` is deliberately public in both browser applications for exactly this reason.

### 403 immediately after a successful login

The user authenticated but arrived with **no authorities**. Almost always one of these two:

1. **The realm-roles mapper is missing from the ID token.** Keycloak puts realm roles in the *access*
   token by default; `oauth2Login` reads the *ID* token. Both web clients here carry a realm-roles
   mapper with `id.token.claim=true`. Without it: successful login, zero roles, 403 everywhere.
2. **No `GrantedAuthoritiesMapper`.** Keycloak nests roles under `realm_access.roles`; Spring
   Security does not know that shape and will not find them on its own.

### The page says I have no roles, but I could reach it

Not a bug in your realm — a Java one. `OidcUser.getAuthorities()` returns the principal's
**unmapped** authorities (`OIDC_USER`, `SCOPE_*`), while the access rules use the **mapped**
authorities on the `Authentication`. Read the wrong one and a page that required `ROLE_app-a-user`
reports that you have no roles at all. Both UI controllers here read the `Authentication`
deliberately, with a comment saying why.

---

## Bearer tokens and APIs

### My API returns 302 to the login page instead of 401

The browser filter chain matched the API request. Spring Security uses the **first** chain whose
`securityMatcher` matches, so the narrow one must be declared first. In Application B:

```java
@Bean @Order(1) SecurityFilterChain apiFilterChain(...)      // securityMatcher("/api/**")
@Bean @Order(2) SecurityFilterChain browserFilterChain(...)  // everything else
```

Swap the orders and every API call gets redirected to a login form.

### 401 with a valid-looking token — audience

Keycloak issues `"aud": "account"` by default. Both resource servers here validate:

```yaml
spring.security.oauth2.resourceserver.jwt.audiences: [app-b-api]   # or app-c-api
```

That passes only because the calling clients carry a hardcoded **audience mapper**. Remove either
side and every call is a bare 401 with nothing informative logged. Worth doing on purpose once:
delete the `audience-app-b-api` mapper from `app-a-m2m` in the admin console, watch Application A's
scheduler start failing, then add it back.

Decode the token and compare `aud` with the configured `audiences` — that is the whole check.

### 401 — issuer mismatch

`issuer-uri` must equal the `iss` claim **character for character**, including the port. This is why
`verify-keycloak.ps1` compares the discovery document's reported `issuer` against what the
applications expect. `localhost` and `127.0.0.1` are not interchangeable here.

A domain A token presented to Application C fails this check, by design — that *is* the cross-domain
isolation demonstration, not a misconfiguration.

### 403 on the API with the right role name

Check the prefix. `hasRole("app-b-user")` looks for the authority `ROLE_app-b-user`. The converters
here add the `ROLE_` prefix; if you write your own and forget it, `hasRole` silently never matches.

### `servletRequest cannot be null` from a scheduled job

The full story is in [flows.md](flows.md#flow-2--machine-to-machine-same-sso-domain). Short version:
Boot's auto-configured `DefaultOAuth2AuthorizedClientManager` is request-bound and cannot work on a
scheduler thread. Use `AuthorizedClientServiceOAuth2AuthorizedClientManager` there, and remember that
declaring any manager bean disables the auto-configured one — so declare both if you need both.

---

## Startup and build

### `Table "REPORT" not found` on startup

`data.sql` runs **before** Hibernate creates the schema unless you defer it:

```yaml
spring.jpa.defer-datasource-initialization: true
```

Set in both Application B and Application C.

### The application will not start at all

It fetches the OpenID Connect discovery document at startup, so an unreachable issuer is fatal. Start
both Keycloak instances first and run `.\scripts\verify-keycloak.ps1`.

### Maven cannot resolve `spring-boot-starter-web`

Spring Boot 4 renamed several starters. The old names still resolve but are deprecated:

| Spring Boot 3 | Spring Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |

Spring Boot 4 also defaults to **Jackson 3** (group id `tools.jackson`), which matters if you add
custom serializers.

### PowerShell: a script fails on a line that clearly worked

In Windows PowerShell 5.1, redirecting a native executable's stderr with `2>&1` wraps each line in an
error record, and with `$ErrorActionPreference = 'Stop'` that aborts the script even when the exit
code was 0. This bit `install-keycloak.ps1`: `java -version` writes to stderr. Use `java --version`
(two dashes), which writes to stdout.

---

## Watching it happen

Uncomment in any `application.yml`:

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.web.client: DEBUG
```

Very informative the first time a flow misbehaves — it prints the redirects, the token requests and
the authority mapping — and far too noisy to leave on.

To read a token, paste it into <https://jwt.io>, or use `.\scripts\get-token.ps1 -Scenario decode`.
Remember that decoding is not validating: only the resource server's signature check makes a token
trustworthy.

If you are driving the APIs from the Bruno API client, [../bruno.md](../bruno.md) has a section of
Bruno-specific failures — cached OAuth2 tokens, `credentials_id` collisions and the callback-URL
registration Keycloak insists on.
