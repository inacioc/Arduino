# The four flows, request by request

Each section traces what actually goes over the wire, and names the configuration that makes it
work. Read alongside the running lab: every claim here is visible in the applications' logs or pages.

---

## Flow 1 — Browser SSO across two applications

**What the user does:** signs in once at Application A, then opens Application B.

```
 browser                    App A :8081              Keycloak-A :8080         App B :8082
    │                          │                          │                       │
    │ GET /ui/profile          │                          │                       │
    ├─────────────────────────►│                          │                       │
    │ 302 /oauth2/authorization/keycloak-a                 │                       │
    │◄─────────────────────────┤                          │                       │
    │ 302 to Keycloak /auth?client_id=app-a-web&code_challenge=...                 │
    │◄─────────────────────────┤                          │                       │
    │ GET /auth?...            │                          │                       │
    ├─────────────────────────────────────────────────────►│                       │
    │ 200 login form           │                          │                       │
    │◄─────────────────────────────────────────────────────┤                       │
    │ POST username + password │                          │                       │
    ├─────────────────────────────────────────────────────►│                       │
    │                          │      ← Keycloak sets its own SSO session cookie   │
    │ 302 to /login/oauth2/code/keycloak-a?code=...        │                       │
    │◄─────────────────────────────────────────────────────┤                       │
    │ GET /login/oauth2/code/keycloak-a?code=...           │                       │
    ├─────────────────────────►│                          │                       │
    │                          │ POST /token (code + client secret + PKCE verifier)│
    │                          ├─────────────────────────►│                       │
    │                          │ ID token + access token  │                       │
    │                          │◄─────────────────────────┤                       │
    │ 302 /ui/profile + JSESSIONID for App A               │                       │
    │◄─────────────────────────┤                          │                       │
```

Now the second application, in the same browser:

```
    │ GET /ui/reports          │                          │                       │
    ├──────────────────────────────────────────────────────────────────────────────►│
    │ 302 to Keycloak /auth?client_id=app-b-web&...        │                       │
    │◄──────────────────────────────────────────────────────────────────────────────┤
    │ GET /auth?...  (carries Keycloak's SSO cookie)       │                       │
    ├─────────────────────────────────────────────────────►│                       │
    │ 302 straight back with a NEW code - NO password form │                       │
    │◄─────────────────────────────────────────────────────┤                       │
    │ ... App B exchanges its own code for its own tokens ...                       │
```

**The point:** the redirect to Keycloak still happens for Application B. What is skipped is the
*password prompt*, because Keycloak recognises the SSO cookie it set during the first login. Each
application ends up with its own session and its own tokens; the shared thing is the Keycloak
session, which is why logging out of one application does not by itself log you out of the other.

**Configuration that matters**

| Where | Setting |
|---|---|
| both realms clients | `redirectUris` must contain `http://localhost:808{1,2}/login/oauth2/code/keycloak-a` |
| `application.yml` | the registration id (`keycloak-a`) determines that callback path |
| both web clients | realm-roles protocol mapper with `id.token.claim=true`, else zero authorities |
| both apps | `GrantedAuthoritiesMapper` reading `realm_access.roles` |

---

## Flow 2 — Machine to machine, same SSO domain

**What happens:** no browser, no user, no session. A `@Scheduled` method on a scheduler thread.

```
  App A :8081                          Keycloak-A :8080            App B :8082
      │                                      │                         │
      │ POST /token                          │                         │
      │   grant_type=client_credentials      │                         │
      │   client_id=app-a-m2m                │                         │
      │   client_secret=app-a-m2m-secret     │                         │
      ├─────────────────────────────────────►│                         │
      │ access_token (aud=app-b-api,         │                         │
      │   azp=app-a-m2m,                     │                         │
      │   realm_access.roles=[app-b-api-reader])                        │
      │◄─────────────────────────────────────┤                         │
      │ GET /api/reports                                               │
      │   Authorization: Bearer <token>                                │
      ├───────────────────────────────────────────────────────────────►│
      │                                      │  App B fetches JWKS once│
      │                                      │◄────────────────────────┤
      │                                      │  validates sig, iss,    │
      │                                      │  exp, aud, then role    │
      │ 200 { application, resource, caller, items }                    │
      │◄───────────────────────────────────────────────────────────────┤
```

There is **one** round trip to Keycloak, not one per call: the authorized-client manager caches the
token until it expires, then quietly fetches another.

**The trap.** Spring Boot auto-configures a single `OAuth2AuthorizedClientManager`, and it is
`DefaultOAuth2AuthorizedClientManager`, which resolves the current servlet request from
`RequestContextHolder`. On a scheduler thread there is no request, so it fails:

```
java.lang.IllegalArgumentException: servletRequest cannot be null
```

The fix is `AuthorizedClientServiceOAuth2AuthorizedClientManager`, which keeps tokens in an
`OAuth2AuthorizedClientService` instead of the HTTP session and needs no request at all. Because
declaring *any* manager bean disables the auto-configured one, this lab declares **both** explicitly
— see [OAuth2ClientConfig](../app-a/src/main/java/com/example/sso/appa/config/OAuth2ClientConfig.java).

---

## Flow 3 — Machine to machine, across SSO domains

Structurally identical to flow 2, and that is the lesson: from the caller's side, "another SSO
domain" is just a different token endpoint and a different set of credentials.

```
  App A :8081                          Keycloak-C :8090            App C :8083
      │ POST /token                          │                         │
      │   client_id=app-a-federated-m2m      │                         │
      │   client_secret=app-a-federated-m2m-secret                     │
      ├─────────────────────────────────────►│                         │
      │ access_token (iss=…:8090/realms/sso-domain-c, aud=app-c-api)    │
      │◄─────────────────────────────────────┤                         │
      │ GET /api/inventory + Bearer                                     │
      ├───────────────────────────────────────────────────────────────►│
      │ 200                                                             │
      │◄───────────────────────────────────────────────────────────────┤
```

What makes it a genuinely separate domain:

- **Separate credentials.** An administrator of domain C created `app-a-federated-m2m` and gave
  Application A its secret. Domain A had no say in it.
- **Separate signing keys.** Application C fetches JWKS from `:8090` only. A domain A token fails the
  signature check before any role is considered.
- **Separate users.** `alice` does not exist in domain C; `frank` does not exist in domain A.
- **No federation.** No identity brokering, no trust relationship. Application A simply holds two
  independent sets of credentials — one per domain.

Verify the isolation directly:

```powershell
.\scripts\get-token.ps1 -Scenario cross-domain-rejected
# domain A token -> Application C : 401
# domain C token -> Application C : 200
```

If the two domains had been two *realms in one Keycloak process*, all four bullets above would still
hold — realms are fully isolated. Two installations were chosen here to make the separation
physically obvious, and because it forces you to deal with the management-port collision that real
multi-instance setups hit.

---

## Flow 4 — Delegated user identity (token relay)

**What happens:** a request arrives from a logged-in human, and Application A calls Application B
*as that human* rather than as itself.

```
 browser          App A :8081                                    App B :8082
    │ GET /ui/call-app-b-as-me (session cookie)                       │
    ├────────────────────────►│                                       │
    │                         │ look up the OAuth2AuthorizedClient    │
    │                         │ stored for this user at login;        │
    │                         │ refresh it if expired                 │
    │                         │                                       │
    │                         │ GET /api/reports                      │
    │                         │   Authorization: Bearer <alice's token>│
    │                         ├──────────────────────────────────────►│
    │                         │ 200 caller.preferred_username = alice │
    │                         │◄──────────────────────────────────────┤
    │ 200 page showing both identities side by side                   │
    │◄────────────────────────┤                                       │
```

This path *does* use the request-bound `DefaultOAuth2AuthorizedClientManager` — correctly, because
there is a request, and because the token being forwarded belongs to whoever is browsing. The
default `PrincipalResolver` reads the `SecurityContext`, so no configuration is needed beyond
pointing the interceptor at the `keycloak-a` registration.

### Choosing between flow 2 and flow 4

|  | application identity | delegated user identity |
|---|---|---|
| Use for | schedulers, batch jobs, system-to-system sync | work that exists only because a user asked |
| Lifetime | independent of any user; renews indefinitely | dies with the user's session or refresh token |
| Downstream authorization | coarse — whatever the service account holds | exactly the user's own permissions |
| Auditing | "Application A did this" | "alice did this, via Application A" |
| If the credential leaks | full service-account access until rotated | one user, short-lived |

A scheduled job must not relay a user token. There is no user, and borrowing the last one that
happened to log in produces a job that fails unpredictably and grants itself that person's
permissions.

---

## What each application validates

| | Application B `/api/**` | Application C `/api/**` |
|---|---|---|
| signature | JWKS from `:8080/realms/sso-domain-a` | JWKS from `:8090/realms/sso-domain-c` |
| `iss` | `…:8080/realms/sso-domain-a` | `…:8090/realms/sso-domain-c` |
| `aud` must contain | `app-b-api` | `app-c-api` |
| `exp` | yes | yes |
| authority required | `ROLE_app-b-api-reader` **or** `ROLE_app-b-user` | `ROLE_app-c-api-reader` |

Failing any of the first four gives **401** — the caller is not authenticated. Passing them all but
lacking the role gives **403** — authenticated, not authorized. That distinction is worth internalising;
it is the fastest way to locate a problem.
