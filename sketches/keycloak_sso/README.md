# Keycloak SSO Lab — 3 applications, 2 SSO domains

A runnable lab for exploring how **users** and **applications** authenticate through SSO. Everything
runs on one Windows machine: no Docker, no Kubernetes, no cloud.

Four flows are demonstrated side by side, and — more importantly — made *observable*: each
downstream application echoes back the identity it extracted from the token that arrived, so you
never have to guess which credential was actually used.

| # | Flow | Who is authenticated | Grant |
|---|------|----------------------|-------|
| 1 | Browser SSO across two applications | a human | `authorization_code` |
| 2 | Scheduled call A → B, same SSO domain | Application A itself | `client_credentials` |
| 3 | Scheduled call A → C, **different** SSO domain | Application A, by a foreign IdP | `client_credentials` |
| 4 | User-triggered call A → B (token relay) | the logged-in human, forwarded | relayed access token |

**Why flows 2 and 4 both exist:** a scheduled job has no user session, so there is no user token to
forward — and stashing one would break the job the moment that user logged out. Background work
needs an identity of its own (a service account). Flow 4 hits the *same* endpoint as flow 2, with a
user's token, so the difference is visible in one place.

---

## Verified environment

Every step below was executed on this machine, and the results are recorded in
[docs/verification.md](docs/verification.md).

| | |
|---|---|
| JDK | Temurin **25.0.3** (`JAVA_HOME=C:\Apps\jdks\jdk-25.0.3+9`) — Keycloak 26.7 supports OpenJDK 17, 21 and 25 |
| Maven | **3.9.1** |
| Spring Boot | **4.0.7** (Spring Framework 7.0.8, Spring Security 7.0.6) |
| Keycloak | **26.7.0**, ZIP distribution |

---

## Topology

```
  Keycloak-A  :8080  (mgmt :9000)          Keycloak-C  :8090  (mgmt :9090)
  realm  sso-domain-a                      realm  sso-domain-c
  C:\Apps\keycloak-a                       C:\Apps\keycloak-c
        ▲          ▲            ▲                      ▲
  login │    login │      client_credentials      client_credentials
        │          │            │                      │
  ┌─────┴───┐  ┌───┴─────┐      │                ┌─────┴─────┐
  │  App A  │  │  App B  │◄─────┘  scheduled     │   App C   │◄──── scheduled
  │  :8081  │  │  :8082  │◄─ token relay (user)  │   :8083   │      (cross-domain)
  └─────────┘  └─────────┘                       └───────────┘
   OIDC client  OIDC client + resource server      resource server only
```

- **Application A** (`:8081`) — browser login, owns both scheduled jobs and the token-relay page. No database.
- **Application B** (`:8082`) — *both* a browser application (so SSO with A is demonstrable) *and* a resource server on `/api/**`. H2 in-memory database.
- **Application C** (`:8083`) — resource server only, trusting the **second** Keycloak. H2 in-memory database.

The two identity providers are **two separate installations** with separate databases, keys, users
and admin consoles. No trust relationship exists between them.

---

## Running it — the short version

Five terminals. Steps 2 and 3 stay open, so do 4–6.

```powershell
cd c:\Apps\Learn\GibHub\Arduino\sketches\keycloak_sso

# 1. once: download Keycloak and create both instances (~250 MB, one download)
.\scripts\install-keycloak.ps1

# 2. terminal 1 — SSO domain A
.\keycloak\domain-a\start-keycloak-a.ps1

# 3. terminal 2 — SSO domain C
.\keycloak\domain-c\start-keycloak-c.ps1

# 4. check both domains BEFORE starting any Java
.\scripts\verify-keycloak.ps1

# 5. build once
mvn clean install

# 6. terminals 3, 4, 5
.\scripts\run-app.ps1 b
.\scripts\run-app.ps1 c
.\scripts\run-app.ps1 a
```

Then open <http://localhost:8081/> and sign in as `alice` / `Alice#2026`.

> Start B and C before A. Application A's scheduler begins calling them after 10 seconds; if they
> are not up yet you will see connection failures in A's log until they are. Nothing breaks — the
> next interval succeeds — but it is less confusing this way.

### Step 4 matters

`verify-keycloak.ps1` checks that both discovery documents are reachable, that each reports exactly
the issuer the applications are configured to trust, and that a `client_credentials` token can be
obtained — which also proves the client secrets match. The applications read those discovery
documents at startup and fail fast if an issuer is unreachable, so a two-second check here saves
a confusing Java stack trace.

---

## What to click, and what to look for

### Flow 1 — browser SSO

1. Open <http://localhost:8081/>, sign in as **alice / Alice#2026**.
2. You land on **Your identity**. The authorities list shows `ROLE_app-a-user` and `ROLE_app-b-user`,
   mapped from the `realm_access.roles` claim.
3. Click **Open Application B**. You arrive at <http://localhost:8082/ui/reports> **with no password
   prompt**.

A different application, on a different port, with its own client id and secret — one login. Your
browser was redirected to Keycloak, which recognised its own session cookie and immediately issued
Application B a fresh authorization code. The SSO session lives in Keycloak, not in either
application.

To *see* that: click **Log out** in Application A (a local logout — it clears only Application A's
session), then sign in again. No password is requested, because the Keycloak session is still alive.
Enabling RP-initiated single logout is a one-line change, marked in
[app-a/…/SecurityConfig.java](app-a/src/main/java/com/example/sso/appa/config/SecurityConfig.java).

### Flow 1b — authentication is not authorization

Sign in as **bob / Bob#2026** (an incognito window avoids logging alice out) and open
<http://localhost:8082/ui/reports>. You get **403**, with no password prompt.

Bob is a perfectly authenticated user of SSO domain A — the SSO redirect succeeded silently. He just
lacks the `app-b-user` role. `carol` is the mirror case: fine in B, 403 in A.

### Flows 2 and 3 — machine to machine

Watch **Application A's terminal**. Every 30 seconds:

```
[scheduled -> app-b] OK  | app-b accepted us as application 'app-a-m2m' (service account);
                           issuer=http://localhost:8080/realms/sso-domain-a; roles=[app-b-api-reader]; 4 item(s)
[scheduled -> app-c] OK  | app-c accepted us as application 'app-a-federated-m2m' (service account);
                           issuer=http://localhost:8090/realms/sso-domain-c; roles=[app-c-api-reader]; 4 item(s)
```

Or open <http://localhost:8081/ui/machine-calls> for the same thing rendered, with the full claim
breakdown. Note the different `issuer` values: the second job's token was minted by a completely
different identity provider, using a completely different set of credentials.

### Flow 4 — the same endpoint, a different identity

Open <http://localhost:8081/ui/call-app-b-as-me>. Two cards, both hitting `GET /api/reports` on
Application B:

| | relayed (user) | scheduled (machine) |
|---|---|---|
| `azp` | `app-a-web` | `app-a-m2m` |
| `preferred_username` | `alice` | `service-account-app-a-m2m` |
| `realm roles` | `[app-a-user, app-b-user]` | `[app-b-api-reader]` |
| service account? | no | yes |

That endpoint accepts either `app-b-api-reader` or `app-b-user`, which is why one URL can serve both
a machine and a human.

### Without a browser

```powershell
.\scripts\get-token.ps1                       # 8 checks, decoded tokens, pass/fail summary
.\scripts\get-token.ps1 -Scenario decode -User alice   # just print alice's token and claims
```

This fetches tokens straight from the token endpoints and prints the decoded payloads, so you can
read the claims that drive every authorization decision. It also proves domain isolation: a domain A
token gets **401** from Application C, while a domain C token gets **200**.

Prefer an API client? The same checks exist as a [Bruno](https://www.usebruno.com/) collection in
[bruno/keycloak-sso-lab/](bruno/keycloak-sso-lab/) — git-native `.bru` files with per-claim
assertions and a CLI runner for CI. [bruno.md](bruno.md) explains every request. Note that the
collection has not been executed against the live lab, unlike the PowerShell checks above.

---

## Test users

**SSO domain A** (`http://localhost:8080/realms/sso-domain-a`)

| Username | Password | Realm roles | Demonstrates |
|---|---|---|---|
| `alice` | `Alice#2026` | `app-a-user`, `app-b-user` | SSO across both applications |
| `bob` | `Bob#2026` | `app-a-user` | authenticated, but 403 in Application B |
| `carol` | `Carol#2026` | `app-b-user` | the mirror case — 403 in Application A |
| `dave` | `Dave#2026` | `app-a-user`, `app-b-user`, `sso-admin` | an extra role for role-gated views |

**SSO domain C** (`http://localhost:8090/realms/sso-domain-c`)

| Username | Password | Realm roles | Demonstrates |
|---|---|---|---|
| `frank` | `Frank#2026` | `app-c-api-reader` | a user who exists *only* in the other domain |

Both Keycloak admin consoles: **admin / admin** (<http://localhost:8080> and <http://localhost:8090>).

> These credentials, and the client secrets below, are committed in plain text on purpose: the lab
> has to be reproducible from a clean checkout. Do not carry this pattern into anything real —
> secrets belong outside the repository, and the direct-access-grant flow enabled here for testing
> should stay switched off in production.

## Clients and secrets

**Domain A** — [keycloak/domain-a/realm-sso-domain-a.json](keycloak/domain-a/realm-sso-domain-a.json)

| clientId | Flows | Secret | Role |
|---|---|---|---|
| `app-a-web` | `authorization_code` (+ direct grant for testing) | `app-a-web-secret` | humans logging in to A |
| `app-b-web` | `authorization_code` (+ direct grant) | `app-b-web-secret` | humans logging in to B |
| `app-a-m2m` | **service account only** | `app-a-m2m-secret` | A's own identity, calling B |
| `app-b-api` | none | — | exists only to name B's audience |

**Domain C** — [keycloak/domain-c/realm-sso-domain-c.json](keycloak/domain-c/realm-sso-domain-c.json)

| clientId | Flows | Secret | Role |
|---|---|---|---|
| `app-a-federated-m2m` | service account only | `app-a-federated-m2m-secret` | A's identity **in this foreign domain** |
| `app-c-api` | none | — | names C's audience |
| `domain-c-test-cli` | direct grant | `domain-c-test-cli-secret` | lets scripts fetch a domain C user token |

---

## Two things the realm files do that are easy to miss

Both are configured for you; both cause silent, confusing failures when absent.

**1. An audience mapper on every client whose tokens reach an API.** Keycloak issues
`"aud": "account"` by default. The resource servers validate `audiences: [app-b-api]` /
`[app-c-api]`, so without a hardcoded audience mapper adding that value, every call is a bare 401
with nothing useful in the log.

**2. A realm-roles mapper with `id.token.claim = true` on the browser clients.** Realm roles ride in
the *access* token by default, not the ID token. `oauth2Login` reads the ID token, so without this
mapper a user logs in successfully and receives **zero** authorities — every `/ui/**` page answers
403 immediately after a successful login.

There is a related trap on the Java side, worth knowing because it looks like a bug in your config:
`OidcUser.getAuthorities()` returns the principal's *unmapped* authorities, while the access rules
use the `Authentication`'s *mapped* ones. Read the wrong one and your page reports no roles on a page
that required a role to reach. Both UI controllers here deliberately read the `Authentication`.

## Editing a realm and seeing nothing change

`start-dev --import-realm` **skips realms that already exist**, silently — the server starts
normally and keeps serving the old configuration.

```powershell
# stop both Keycloak windows (Ctrl+C) first
.\scripts\reset-keycloak.ps1
# then start them again; realms are imported fresh
```

Individual instances also accept `-Fresh`:
`.\keycloak\domain-a\start-keycloak-a.ps1 -Fresh`.

---

## Layout

```
keycloak_sso/
├── README.md                    ← you are here
├── pom.xml                      ← parent: Spring Boot 4.0.7, Java 25
├── app-a/                       ← :8081  OIDC client + schedulers + token relay
├── app-b/                       ← :8082  OIDC client + resource server + H2
├── app-c/                       ← :8083  resource server + H2
├── keycloak/
│   ├── domain-a/                ← realm JSON, start script, settings walkthrough
│   └── domain-c/                ← same, for the second domain
├── scripts/
│   ├── install-keycloak.ps1     ← download once, install twice
│   ├── verify-keycloak.ps1      ← run before starting the applications
│   ├── run-app.ps1 a|b|c
│   ├── get-token.ps1            ← token-level checks, no browser
│   └── reset-keycloak.ps1       ← force a realm re-import
├── bruno.md                     ← testing it all with the Bruno API client
├── bruno/keycloak-sso-lab/      ← the Bruno collection itself (13 requests)
└── docs/
    ├── flows.md                 ← each flow, request by request
    ├── troubleshooting.md       ← 401 / 403 / redirect_uri / 302-instead-of-401
    └── verification.md          ← what was actually run, and the output
```

Each `keycloak/domain-*/README.md` explains what every client, mapper and role in that realm is for,
and how to reproduce it by clicking through the admin console — useful if you would rather learn the
configuration than import it.

## Ports

| Port | What |
|---|---|
| 8080 | Keycloak A (SSO domain A) |
| 9000 | Keycloak A management (health/metrics) |
| 8090 | Keycloak C (SSO domain C) |
| 9090 | Keycloak C management — **must** be overridden, or the second instance cannot start |
| 8081 / 8082 / 8083 | Application A / B / C |
