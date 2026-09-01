# Testing this lab with Bruno

[Bruno](https://www.usebruno.com/) is a git-native API client: collections are plain-text `.bru`
files that live in your repository, not in a vendor's cloud. That makes it a good fit here — the
requests can sit next to the applications they exercise, and a change to a client secret shows up in
`git diff` like any other change.

This guide rebuilds every check in [scripts/get-token.ps1](scripts/get-token.ps1) as a Bruno
collection, and explains what each one proves. It assumes you have the lab running as described in
[README.md](README.md).

**How this relates to the other documents:**

| Document | What it gives you | How Bruno uses it |
|---|---|---|
| [README.md](README.md) | how to start everything, users, client ids and secrets | the values that go into the environment file |
| [docs/flows.md](docs/flows.md) | the four flows, request by request | the reason each Bruno request exists |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 401 vs 403, audience, issuer, redirect URI | what to read when a Bruno request fails |
| [docs/verification.md](docs/verification.md) | results already recorded from PowerShell | the expected outcomes the Bruno tests assert |
| [keycloak/domain-a/README.md](keycloak/domain-a/README.md), [domain-c](keycloak/domain-c/README.md) | every client, role and mapper | why a token is accepted or rejected |

---

## Contents

1. [Install](#1-install)
2. [Bruno's callback URL — already registered](#2-brunos-callback-url--already-registered)
3. [Collection layout](#3-collection-layout)
4. [`bruno.json` and `collection.bru`](#4-brunojson-and-collectionbru)
5. [The environment file](#5-the-environment-file)
6. [Sanity checks](#6-sanity-checks-no-auth)
7. [Flow 2 and 3 — machine to machine](#7-flows-2-and-3--machine-to-machine)
8. [Flow 4 — a user's identity](#8-flow-4--a-users-identity)
9. [Flow 1 — the full browser login](#9-flow-1--the-full-browser-login-authorization-code)
10. [Negative tests](#10-negative-tests--the-ones-that-teach-the-most)
11. [Inspecting a token's claims](#11-inspecting-a-tokens-claims)
12. [Running the collection from the CLI](#12-running-the-collection-from-the-cli)
13. [What Bruno can and cannot prove](#13-what-bruno-can-and-cannot-prove)
14. [Bruno-specific troubleshooting](#14-bruno-specific-troubleshooting)

---

## 1. Install

The desktop app, for building and eyeballing requests:

```powershell
winget install Bruno.Bruno
```

The CLI, for running the whole collection headlessly (this is what makes it a regression suite):

```powershell
npm install -g @usebruno/cli
```

> Bruno still fully supports `.bru`, but newer versions also offer an OpenCollection YAML format for
> new collections. Everything below uses `.bru`, which is what the desktop app writes by default and
> what the syntax in this guide was checked against.

---

## 2. Bruno's callback URL — already registered

**Relevant only to the authorization-code flow in [section 9](#9-flow-1--the-full-browser-login-authorization-code).** Every other request works without it.

Bruno never actually navigates to the callback URL — it intercepts the redirect from the identity
provider internally. But **Keycloak still validates `redirect_uri` against the client's registered
list**, so the URL Bruno sends must be registered, or you get
`Invalid parameter: redirect_uri` before the login form even appears.

`http://localhost:9876/callback` is therefore already on the `app-a-web` client in
[keycloak/domain-a/realm-sso-domain-a.json](keycloak/domain-a/realm-sso-domain-a.json):

```json
      "redirectUris": [
        "http://localhost:8081/login/oauth2/code/keycloak-a",
        "http://localhost:9876/callback"
      ],
```

**If your Keycloak was already running before this entry existed, it does not have it yet.**
`--import-realm` silently skips realms that already exist, so the file on disk and the realm in the
server can disagree. Force a re-import:

```powershell
# stop both Keycloak windows (Ctrl+C) first
.\scripts\reset-keycloak.ps1
.\keycloak\domain-a\start-keycloak-a.ps1
```

Or add it in the admin console instead, which needs no restart:
<http://localhost:8080> → realm `sso-domain-a` → *Clients* → `app-a-web` → *Settings* → add
`http://localhost:9876/callback` to *Valid redirect URIs* → *Save*.

Use a different port for Bruno? Change `brunoCallback` in `environments/local.bru` and register the
matching URI — the two must agree exactly.

> **Do not create a brand-new Keycloak client for Bruno** unless you also add the audience mapper to
> it. A fresh client issues tokens with `"aud": "account"`, and both resource servers validate
> `aud`, so every call would come back 401 for reasons that have nothing to do with Bruno. Reusing
> `app-a-web` and `app-a-m2m` avoids this. See
> [docs/troubleshooting.md](docs/troubleshooting.md#401-with-a-valid-looking-token--audience).

---

## 3. Collection layout

**The collection exists in this repository** at
[bruno/keycloak-sso-lab/](bruno/keycloak-sso-lab/) — open it with *Open Collection* in the Bruno
app and it is ready to run. The rest of this guide walks through what each file does and why; the
listings below are the files as they are on disk, not something you need to type out.

Bruno maps folders to folders and requests to `.bru` files, so the tree *is* the collection:

```
bruno/keycloak-sso-lab/
├── bruno.json
├── collection.bru
├── environments/
│   └── local.bru
├── 01-sanity/
│   ├── app-b-health.bru
│   └── app-c-health.bru
├── 02-machine-to-machine/
│   ├── app-b-reports-as-machine.bru
│   ├── app-b-whoami-as-machine.bru
│   └── app-c-inventory-as-machine.bru
├── 03-user-identity/
│   ├── app-b-reports-as-alice.bru
│   ├── app-b-reports-as-bob.bru
│   └── app-b-reports-authcode.bru
├── 04-negative/
│   ├── app-b-no-token.bru
│   ├── app-c-no-token.bru
│   ├── app-c-with-domain-a-token.bru
│   └── app-c-inventory-as-frank.bru
└── 05-token-inspection/
    └── decode-machine-token.bru
```

The `seq` value in each `meta` block controls run order within a folder; folders run in name order,
which is why they are numbered.

Every request carries a `docs` block explaining what it proves, which flow in
[docs/flows.md](docs/flows.md) it belongs to, and which `scripts/get-token.ps1` scenario it mirrors.
Those blocks render in the *Docs* tab of the app, so the explanations travel with the requests.

### `.bru` syntax in 30 seconds

```
blockName {
  key: value
  ~disabledKey: value      # a leading ~ disables the line
}
```

Blocks used below: `meta`, `get`/`post`, `headers`, `body:form-urlencoded`, `auth:oauth2`,
`vars:post-response`, `assert`, `script:post-response`, `tests`, `docs`.

---

## 4. `bruno.json` and `collection.bru`

**`bruno/keycloak-sso-lab/bruno.json`**

```json
{
  "version": "1",
  "name": "Keycloak SSO Lab",
  "type": "collection",
  "ignore": [
    "node_modules",
    ".git"
  ]
}
```

**`bruno/keycloak-sso-lab/collection.bru`** — collection-wide defaults. Auth is left at `none` here
because different requests deliberately authenticate against *different* SSO domains; putting auth
at the collection level would hide the very distinction this lab is about.

```
meta {
  type: collection
}

auth {
  mode: none
}

docs {
  Exercises the three applications and two SSO domains described in ../../README.md.

  Every request states which identity it presents and which SSO domain issued it.
  Start both Keycloak instances and all three applications first; see ../../README.md.
}
```

---

## 5. The environment file

**`bruno/keycloak-sso-lab/environments/local.bru`**

```
vars {
  issuerA: http://localhost:8080/realms/sso-domain-a
  issuerC: http://localhost:8090/realms/sso-domain-c
  tokenUrlA: http://localhost:8080/realms/sso-domain-a/protocol/openid-connect/token
  tokenUrlC: http://localhost:8090/realms/sso-domain-c/protocol/openid-connect/token
  authUrlA: http://localhost:8080/realms/sso-domain-a/protocol/openid-connect/auth
  appA: http://localhost:8081
  appB: http://localhost:8082
  appC: http://localhost:8083
  brunoCallback: http://localhost:9876/callback

  clientAWeb: app-a-web
  clientAWebSecret: app-a-web-secret
  clientAMachine: app-a-m2m
  clientAMachineSecret: app-a-m2m-secret
  clientCMachine: app-a-federated-m2m
  clientCMachineSecret: app-a-federated-m2m-secret
  clientCTestCli: domain-c-test-cli
  clientCTestCliSecret: domain-c-test-cli-secret

  aliceUser: alice
  alicePassword: Alice#2026
  bobUser: bob
  bobPassword: Bob#2026
  frankUser: frank
  frankPassword: Frank#2026
}
```

Secrets are in plain text here for the same reason they are in the realm JSON — the lab has to be
reproducible from a clean checkout. In anything real, list them in a `vars:secret` block instead, so
Bruno keeps the values out of the committed file:

```
vars:secret [
  clientAWebSecret
  clientAMachineSecret
]
```

Select the environment from the dropdown in the top-right of the app, or pass `--env local` to the
CLI.

A few variables are deliberately **reference-only** — no request uses them, and that is the point:

| Variable | Why it is there |
|---|---|
| `appA` | Application A is never called by this collection. Its part of the lab — the schedulers and the token-relay page — is observed in its own log and UI |
| `issuerA`, `issuerC` | the two issuer URLs, for reference. The tests hardcode the expected issuer instead of reading these: an assertion should not derive its expected value from the same configuration the request used, or it can pass against a wrong value |
| `daveUser`, `davePassword` | a spare user with an extra role, handy when experimenting |

The listings that follow show each request in full.

---

## 6. Sanity checks (no auth)

Prove the applications are up before blaming the tokens. These endpoints are `permitAll`.

**`01-sanity/app-b-health.bru`**

```
meta {
  name: App B health
  type: http
  seq: 1
}

get {
  url: {{appB}}/actuator/health
  body: none
  auth: none
}

assert {
  $res.status: 200
  $res.body.status: UP
}

docs {
  No token, and no token needed: /actuator/health is permitAll in Application B's browser chain.
  If this fails, nothing else in the collection will pass - start the applications first.
}
```

**`01-sanity/app-c-health.bru`** is identical with `{{appC}}` and `seq: 2`.

---

## 7. Flows 2 and 3 — machine to machine

This is the `client_credentials` grant: Application A's *own* identity, no user involved. Bruno
plays the part of Application A's scheduler. See
[docs/flows.md](docs/flows.md#flow-2--machine-to-machine-same-sso-domain).

Bruno fetches the token itself — you never paste one.

**`02-machine-to-machine/app-b-reports-as-machine.bru`**

```
meta {
  name: App B reports as app-a-m2m (domain A)
  type: http
  seq: 1
}

get {
  url: {{appB}}/api/reports
  body: none
  auth: oauth2
}

auth:oauth2 {
  grant_type: client_credentials
  access_token_url: {{tokenUrlA}}
  client_id: {{clientAMachine}}
  client_secret: {{clientAMachineSecret}}
  scope: 
  credentials_placement: body
  credentials_id: domainA-machine
  token_placement: header
  token_header_prefix: Bearer
  auto_fetch_token: true
  auto_refresh_token: true
}

assert {
  $res.status: 200
}

tests {
  test("the caller is an application, not a person", function() {
    const caller = res.getBody().caller;
    expect(caller.serviceAccount).to.equal(true);
    expect(caller.clientId).to.equal("app-a-m2m");
    expect(caller.username).to.equal("service-account-app-a-m2m");
  });

  test("domain A issued the token", function() {
    expect(res.getBody().caller.issuer)
      .to.equal("http://localhost:8080/realms/sso-domain-a");
  });

  test("the machine holds only its machine role", function() {
    const roles = res.getBody().caller.roles;
    expect(roles).to.include("app-b-api-reader");
    expect(roles).to.not.include("app-b-user");
  });

  test("the audience mapper did its job", function() {
    expect(res.getBody().caller.audience).to.include("app-b-api");
  });

  test("business data came back", function() {
    expect(res.getBody().items).to.have.lengthOf.at.least(1);
  });
}

docs {
  Flow 2 in docs/flows.md. Equivalent to: .\scripts\get-token.ps1 -Scenario machine-to-b

  Note what is NOT here: no username, no password, no browser. The application authenticates with
  its own client id and secret, and Keycloak returns a token representing the application.

  Keycloak backs every service account with a synthetic user named service-account-<clientId>,
  which is why a machine token still carries a preferred_username.
}
```

**`02-machine-to-machine/app-c-inventory-as-machine.bru`** — the cross-domain twin. Structurally
identical; only the token endpoint and credentials change, which is exactly the point of
[flow 3](docs/flows.md#flow-3--machine-to-machine-across-sso-domains).

```
meta {
  name: App C inventory as app-a-federated-m2m (domain C)
  type: http
  seq: 3
}

get {
  url: {{appC}}/api/inventory
  body: none
  auth: oauth2
}

auth:oauth2 {
  grant_type: client_credentials
  access_token_url: {{tokenUrlC}}
  client_id: {{clientCMachine}}
  client_secret: {{clientCMachineSecret}}
  scope: 
  credentials_placement: body
  credentials_id: domainC-machine
  token_placement: header
  token_header_prefix: Bearer
  auto_fetch_token: true
  auto_refresh_token: true
}

assert {
  $res.status: 200
}

tests {
  test("a DIFFERENT identity provider issued this token", function() {
    expect(res.getBody().caller.issuer)
      .to.equal("http://localhost:8090/realms/sso-domain-c");
  });

  test("Application A used its foreign credentials", function() {
    expect(res.getBody().caller.clientId).to.equal("app-a-federated-m2m");
    expect(res.getBody().caller.serviceAccount).to.equal(true);
  });

  test("the role comes from domain C's own role list", function() {
    expect(res.getBody().caller.roles).to.include("app-c-api-reader");
  });
}

docs {
  Flow 3. Equivalent to: .\scripts\get-token.ps1 -Scenario machine-to-c

  Compare this file with app-b-reports-as-machine.bru side by side: only the URLs and credentials
  differ. There is no federation and no trust between the two Keycloak instances - Application A
  simply holds a second set of credentials, issued by domain C's administrator.

  Use a distinct credentials_id from the domain A request. Sharing one would make Bruno overwrite
  the cached token, and you would end up presenting a domain A token to Application C.
}
```

**`02-machine-to-machine/app-b-whoami-as-machine.bru`** (`seq: 2`) — same auth block, pointing at
`{{appB}}/api/whoami`. Returns just the caller identity, which is handy when you want the claims
without the payload.

> **`credentials_id` matters.** It is the cache key for the fetched token. Two requests sharing an id
> share a token; here that would mean sending a domain A token to Application C and getting a
> confusing 401. One id per (domain, client) pair.

---

## 8. Flow 4 — a user's identity

The same endpoint as section 7, reached with a *human's* token. In the running lab this is what
Application A's `/ui/call-app-b-as-me` page does by relaying the logged-in user's token; Bruno
obtains the equivalent token directly with the password grant.

**`03-user-identity/app-b-reports-as-alice.bru`**

```
meta {
  name: App B reports as alice (user identity)
  type: http
  seq: 1
}

get {
  url: {{appB}}/api/reports
  body: none
  auth: oauth2
}

auth:oauth2 {
  grant_type: password
  access_token_url: {{tokenUrlA}}
  username: {{aliceUser}}
  password: {{alicePassword}}
  client_id: {{clientAWeb}}
  client_secret: {{clientAWebSecret}}
  scope: openid profile email
  credentials_placement: body
  credentials_id: domainA-alice
  token_placement: header
  token_header_prefix: Bearer
  auto_fetch_token: true
  auto_refresh_token: true
}

assert {
  $res.status: 200
}

tests {
  test("a human is behind this call", function() {
    const caller = res.getBody().caller;
    expect(caller.serviceAccount).to.equal(false);
    expect(caller.username).to.equal("alice");
  });

  test("the token was issued to the browser client, not the machine client", function() {
    expect(res.getBody().caller.clientId).to.equal("app-a-web");
  });

  test("alice carries her own roles, not the service account's", function() {
    const roles = res.getBody().caller.roles;
    expect(roles).to.include("app-b-user");
    expect(roles).to.not.include("app-b-api-reader");
  });
}

docs {
  Flow 4. Equivalent to: .\scripts\get-token.ps1 -Scenario user-to-b

  Same URL as 02-machine-to-machine/app-b-reports-as-machine.bru. Run both and compare the caller
  block: same endpoint, same instant, completely different sub, azp and roles. That is the whole
  distinction between "the application did this" and "alice did this".

  /api/reports accepts app-b-api-reader OR app-b-user, which is what lets one endpoint serve both.

  The password grant is enabled on app-a-web for testing only. It posts a password straight to the
  token endpoint, bypassing SSO, MFA and consent, and is deprecated for production use. It is not
  how the running applications authenticate anyone - they use authorization_code (section 9).
}
```

**`03-user-identity/app-b-reports-as-bob.bru`** (`seq: 2`) — the same request with `{{bobUser}}` /
`{{bobPassword}}` and `credentials_id: domainA-bob`, expecting **403**:

```
assert {
  $res.status: 403
}

tests {
  test("bob is authenticated but not authorized: 403, not 401", function() {
    expect(res.getStatus()).to.equal(403);
  });
}

docs {
  Bob's token is completely valid - he is a real user of SSO domain A and Keycloak issued him a
  token without complaint. He simply lacks the app-b-user role.

  401 would mean "I do not know who you are". 403 means "I know exactly who you are, and no".
  Telling those two apart is the fastest way to locate a problem; see docs/troubleshooting.md.
}
```

---

## 9. Flow 1 — the full browser login (authorization code)

This is how the real applications authenticate people. Bruno opens a browser window, you type
alice's password into Keycloak's own login form, and Bruno exchanges the resulting code for tokens.

Uses the callback URL covered in [section 2](#2-brunos-callback-url--already-registered).

**`03-user-identity/app-b-reports-authcode.bru`**

```
meta {
  name: App B reports via authorization_code (real login)
  type: http
  seq: 3
}

get {
  url: {{appB}}/api/reports
  body: none
  auth: oauth2
}

auth:oauth2 {
  grant_type: authorization_code
  callback_url: {{brunoCallback}}
  authorization_url: {{authUrlA}}
  access_token_url: {{tokenUrlA}}
  refresh_token_url: {{tokenUrlA}}
  client_id: {{clientAWeb}}
  client_secret: {{clientAWebSecret}}
  scope: openid profile email
  state: bruno-lab-state
  pkce: true
  credentials_placement: body
  credentials_id: domainA-authcode
  token_source: access_token
  token_placement: header
  token_header_prefix: Bearer
  auto_fetch_token: true
  auto_refresh_token: true
}

assert {
  $res.status: 200
}

tests {
  test("whoever logged in is who Application B sees", function() {
    expect(res.getBody().caller.serviceAccount).to.equal(false);
    expect(res.getBody().caller.username).to.equal("alice");
  });
}

docs {
  Flow 1's token half. Click "Get Access Token", or just send the request with auto_fetch_token on:
  Bruno opens Keycloak's login page. Sign in as alice / Alice#2026.

  pkce: true matches the app-a-web client, which sets pkce.code.challenge.method = S256.

  Bruno never navigates to callback_url - it intercepts the redirect - but Keycloak still validates
  redirect_uri against the client's registered list, which is why section 2 exists.

  This request cannot be run headlessly in CI: it needs a human at a login form. Exclude it from
  `bru run`, or accept that it will fail there.
}
```

---

## 10. Negative tests — the ones that teach the most

A collection that only tests the happy path proves very little. These three prove the security
controls actually engage.

**`04-negative/app-b-no-token.bru`**

```
meta {
  name: App B without a token is 401
  type: http
  seq: 1
}

get {
  url: {{appB}}/api/reports
  body: none
  auth: none
}

assert {
  $res.status: 401
}

tests {
  test("unauthenticated, and told so honestly", function() {
    expect(res.getStatus()).to.equal(401);
  });

  test("401, not a 302 redirect to a login page", function() {
    expect(res.getStatus()).to.not.equal(302);
  });
}

docs {
  The second assertion guards a real mistake. Application B has two filter chains, and the
  /api/** one is declared @Order(1) precisely so bearer-token requests are not swallowed by the
  browser chain. Swap the orders and this request starts answering 302 to Keycloak's login form -
  useless to an API client. See docs/troubleshooting.md.
}
```

**`04-negative/app-c-with-domain-a-token.bru`** — the headline test of the whole lab:

```
meta {
  name: A domain A token must NOT open Application C
  type: http
  seq: 3
}

get {
  url: {{appC}}/api/inventory
  body: none
  auth: oauth2
}

auth:oauth2 {
  grant_type: client_credentials
  access_token_url: {{tokenUrlA}}
  client_id: {{clientAMachine}}
  client_secret: {{clientAMachineSecret}}
  scope: 
  credentials_placement: body
  credentials_id: domainA-machine-crossdomain
  token_placement: header
  token_header_prefix: Bearer
  auto_fetch_token: true
  auto_refresh_token: true
}

assert {
  $res.status: 401
}

tests {
  test("the foreign domain rejects it outright", function() {
    expect(res.getStatus()).to.equal(401);
  });
}

docs {
  A perfectly valid token - it opens Application B without complaint - presented to an application
  in the other SSO domain. Rejected before any role is considered: wrong issuer, and signed with a
  key Application C never fetches.

  This is what "separate SSO domain" means in practice, as opposed to just two folders of config.
  Pair it with 02-machine-to-machine/app-c-inventory-as-machine.bru, which succeeds against the
  same URL using domain C credentials.

  Equivalent to: .\scripts\get-token.ps1 -Scenario cross-domain-rejected
}
```

`04-negative/app-c-no-token.bru` (`seq: 2`) mirrors the first against `{{appC}}`.

**`04-negative/app-c-inventory-as-frank.bru`** (`seq: 4`) completes the isolation proof, and matters
more than it looks. Using the password grant against `domain-c-test-cli`, it fetches a token for
`frank` — a user who exists *only* in SSO domain C — and gets **200** from the same URL that just
refused the domain A token:

```
app-c-with-domain-a-token.bru   domain A machine token -> Application C  ->  401
app-c-inventory-as-frank.bru    domain C user token    -> Application C  ->  200
```

Without the second one, the 401 proves very little: Application C might simply be broken, or
rejecting everything. The pair is what shows the rejection is specifically about *which identity
provider vouched for the caller*. It is the Bruno equivalent of the second half of
`.\scripts\get-token.ps1 -Scenario cross-domain-rejected`.

---

## 11. Inspecting a token's claims

The applications tell you who they think called them, but sometimes you want the raw token — the
equivalent of `.\scripts\get-token.ps1 -Scenario decode`. Ask the token endpoint directly and decode
the payload.

**`05-token-inspection/decode-machine-token.bru`**

```
meta {
  name: Fetch and decode a machine token
  type: http
  seq: 1
}

post {
  url: {{tokenUrlA}}
  body: form-urlencoded
  auth: none
}

headers {
  Content-Type: application/x-www-form-urlencoded
}

body:form-urlencoded {
  grant_type: client_credentials
  client_id: {{clientAMachine}}
  client_secret: {{clientAMachineSecret}}
}

vars:post-response {
  machineAccessToken: $res.body.access_token
}

script:post-response {
  // A JWT is three base64url segments; the middle one is the readable payload.
  // Decoding is NOT validating - only the resource server's signature check makes a token
  // trustworthy. This is for looking, not for deciding anything.
  function decodeJwtPayload(token) {
    const segment = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = segment + '='.repeat((4 - (segment.length % 4)) % 4);
    return JSON.parse(atob(padded));
  }

  const claims = decodeJwtPayload(res.getBody().access_token);
  bru.setVar('tokenClaims', claims);

  console.log('iss                :', claims.iss);
  console.log('aud                :', claims.aud);
  console.log('azp                :', claims.azp);
  console.log('sub                :', claims.sub);
  console.log('preferred_username :', claims.preferred_username);
  console.log('realm roles        :', claims.realm_access && claims.realm_access.roles);
  console.log('expires            :', new Date(claims.exp * 1000).toISOString());
}

tests {
  test("Keycloak returned a bearer token", function() {
    expect(res.getStatus()).to.equal(200);
    expect(res.getBody().token_type.toLowerCase()).to.equal("bearer");
  });

  test("the audience mapper put app-b-api in aud", function() {
    const claims = bru.getVar('tokenClaims');
    const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    expect(aud).to.include("app-b-api");
  });

  test("the service account got its realm role", function() {
    expect(bru.getVar('tokenClaims').realm_access.roles).to.include("app-b-api-reader");
  });

  test("this token represents an application", function() {
    const claims = bru.getVar('tokenClaims');
    expect(claims.preferred_username).to.equal("service-account-" + claims.azp);
  });
}

docs {
  The two assertions in the middle are the two Keycloak mappers that are easy to forget, checked
  directly at the source rather than inferred from a downstream 401:

    - the audience mapper, without which every API call is a bare 401
    - the service-account role assignment, without which every API call is a 403

  See keycloak/domain-a/README.md for what those mappers look like in the admin console.

  Worth breaking on purpose once: remove the audience-app-b-api mapper from app-a-m2m in the
  console, re-run this request, and watch the aud test fail while everything else still passes.

  If atob is unavailable in your Bruno version's sandbox, run with --sandbox=developer and use
  Buffer.from(segment, 'base64').toString('utf8') instead.
}
```

The captured `machineAccessToken` variable is reusable — any later request can send
`Authorization: Bearer {{machineAccessToken}}` by hand instead of letting `auth:oauth2` manage it.
Useful when you want to see exactly one token being used everywhere.

---

## 12. Running the collection from the CLI

The collection is in the repo, so this works from a clean checkout. From `bruno/keycloak-sso-lab/`
— add `npx @usebruno/cli …` instead of `bru …` if you would rather not install the CLI globally:

```powershell
# everything
bru run . -r --env local

# one folder
bru run 02-machine-to-machine --env local

# stop at the first failure
bru run . -r --env local --bail

# reports for CI
bru run . -r --env local --reporter-junit results.xml
bru run . -r --env local --reporter-html results.html
```

Keep secrets out of the committed environment file by injecting them at run time:

```powershell
bru run . -r --env local --env-var clientAMachineSecret=$env:APP_A_M2M_SECRET
```

**Exclude the interactive request.** `app-b-reports-authcode.bru` needs a human at a login form and
will fail headlessly. Either move it to a folder you do not run in CI, or run the folders explicitly:

```powershell
bru run 01-sanity -r --env local
bru run 02-machine-to-machine -r --env local
bru run 04-negative -r --env local
bru run 05-token-inspection -r --env local
```

Prerequisites are the same as for the PowerShell scripts: both Keycloak instances and all three
applications running. Run `.\scripts\verify-keycloak.ps1` first — it fails in two seconds with a
clear message, whereas Bruno would report a wall of connection errors.

---

## 13. What Bruno can and cannot prove

Worth being precise about, because it is the difference between "the tests pass" and "SSO works".

**Bruno covers well**

- Every token-level check in `scripts/get-token.ps1` — all 8 of them, with richer assertions, since
  it can inspect the whole `caller` block rather than just the status code.
- Claim-level verification: audience mappers, realm roles, issuer, `azp`, service-account detection.
- The 401-vs-403 distinction, on real requests.
- Cross-domain isolation.
- Regression running in CI.

**Bruno cannot prove**

- **The actual SSO experience.** The heart of flow 1 is that opening Application B after logging in
  to Application A produces *no password prompt*, because the browser still holds Keycloak's SSO
  cookie. That is a property of a shared browser session across two applications. Bruno's
  authorization-code flow gets you a valid user token, but it does not demonstrate the absence of a
  second prompt. Verify that in a real browser, as
  [README.md](README.md#flow-1--browser-sso) describes — it was verified that way in
  [docs/verification.md](docs/verification.md#5-browser-sso-driven-end-to-end).
- **Session and cookie behaviour** — `JSESSIONID` handling, local logout, the 403-without-a-prompt
  case for bob in the browser.
- **The token relay itself.** Bruno can obtain a user token and call Application B with it, which
  proves the endpoint accepts a user identity. It cannot prove that *Application A* correctly pulled
  the logged-in user's stored token and forwarded it — that is Application A's internal wiring, seen
  at `/ui/call-app-b-as-me`.
- **The scheduler.** Whether `@Scheduled` jobs work on a thread with no HTTP request is a
  server-side concern, visible only in Application A's log.

Treat Bruno as the token-and-API layer of the test pyramid, and the browser walkthrough in the README
as the layer above it. Neither replaces the other.

---

## 14. Bruno-specific troubleshooting

Start with [docs/troubleshooting.md](docs/troubleshooting.md) — most failures here are lab
configuration, not Bruno. These are the ones Bruno adds:

| Symptom | Cause | Fix |
|---|---|---|
| `Invalid parameter: redirect_uri` when fetching an authorization-code token | the URI is in the realm file, but your running Keycloak was imported before it was added — `--import-realm` skips existing realms | [Section 2](#2-brunos-callback-url--already-registered): `reset-keycloak.ps1`, or add it in the console |
| A request that should succeed returns 401, and the token looks fine | two requests share a `credentials_id`, so the cached token is from the wrong domain | give every (domain, client) pair its own `credentials_id` |
| Editing an `auth:oauth2` block changes nothing | the previously fetched token is still cached | clear the saved token in the app (*Get Access Token* → clear), or change `credentials_id` |
| `{{var}}` arrives literally in the request | no environment selected | pick `local` in the top-right dropdown, or pass `--env local` |
| 401 from a client you created yourself | the new Keycloak client has no audience mapper, so `aud` is `account` | reuse `app-a-web` / `app-a-m2m`, or add the mapper — see [keycloak/domain-a/README.md](keycloak/domain-a/README.md) |
| `atob is not defined` in a script | Safe Mode sandbox | run with `--sandbox=developer`, or use the `Buffer` fallback noted in section 11 |
| Everything fails with connection refused | applications or Keycloak not running | `.\scripts\verify-keycloak.ps1`, then start the three applications |

To see the exact bytes on the wire, open the *Timeline* tab on any request in the desktop app — it
shows the token request Bruno made on your behalf as well as the API call, which is usually enough to
tell an auth problem from an authorization one.

---

## Opening the collection

It is already in the repository. In Bruno choose *Open Collection* and point it at
[bruno/keycloak-sso-lab](bruno/keycloak-sso-lab) — the app picks up the `local` environment
automatically; select it from the dropdown in the top-right.

Every listing above is the file as it exists on disk, so the guide and the collection can be read
against each other. **If you change one, change the other** — they are duplicated content and will
otherwise drift.

Building requests through the GUI works just as well for anything you add: Bruno writes the same
`.bru` files, and reading back what it generated is a good way to check the exact spelling of any
field you are unsure of.

> **Not yet executed.** The `.bru` syntax here was checked against Bruno's own parser fixtures and
> the OAuth2 field names against Bruno's documentation, and the collection passes structural checks
> (balanced blocks, every `{{var}}` defined, unique `credentials_id` per domain-and-client). But no
> request has been run against the live lab — unlike the PowerShell checks in
> [docs/verification.md](docs/verification.md), which were. Expect to fix a field name or two on
> first use, and correct this guide alongside the `.bru` file when you do.
