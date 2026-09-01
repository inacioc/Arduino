# SSO domain C

A **second, entirely independent** identity provider. It is here to answer one question: what does it
actually take for an application to call an API that belongs to somebody else's identity provider?

| | |
|---|---|
| Instance | `C:\Apps\keycloak-c` (its own installation and database) |
| Admin console | <http://localhost:8090> — `admin` / `admin` |
| Realm | `sso-domain-c` |
| Issuer | `http://localhost:8090/realms/sso-domain-c` |
| Management port | **9090** — must be overridden, see below |
| Start | `.\start-keycloak-c.ps1` (add `-Fresh` to force a realm re-import) |

Discovery document:
<http://localhost:8090/realms/sso-domain-c/.well-known/openid-configuration>

> **The management port is not optional.** Keycloak 26 always binds an HTTP management interface for
> health and metrics, default **9000**, independent of `--http-port`. Domain A already holds it, so
> this instance passes `--http-management-port=9090`. Without it the second instance fails to start,
> and the error does not obviously point at port 9000.

---

## What makes this a separate domain

Nothing is shared with domain A. In particular:

- **Separate signing keys.** Application C fetches JWKS from `:8090` only, so a domain A token fails
  the signature check before any role is even considered.
- **Separate users.** `alice` does not exist here. `frank` does not exist in domain A.
- **Separate credentials for Application A.** An administrator of *this* realm created
  `app-a-federated-m2m` and handed over its secret. Domain A had no involvement.
- **No identity brokering, no trust relationship.** Application A is simply a client here, holding a
  second set of credentials.

That last point is the answer to "how do I call across SSO domains": you do not federate anything —
the caller registers as a client in the target domain and authenticates there directly.

Prove the isolation:

```powershell
.\scripts\get-token.ps1 -Scenario cross-domain-rejected
# a DOMAIN A token  -> Application C : 401
# a DOMAIN C token  -> Application C : 200
```

---

## What is in the realm

### Clients

| clientId | Standard flow | Direct grant | Service account | Secret |
|---|---|---|---|---|
| `app-a-federated-m2m` | off | off | **on** | `app-a-federated-m2m-secret` |
| `app-c-api` | off | off | off | *(never used)* |
| `domain-c-test-cli` | off | **on** | off | `domain-c-test-cli-secret` |

**`app-a-federated-m2m`** — Application A's identity *in this domain*, and the only reason a
cross-domain call is possible. `client_credentials` only; no human can log in with it. Note there is
no `app-a-web` equivalent: this domain does not authenticate Application A's *users*, only Application A
*itself*.

**`app-c-api`** — no flows; exists so `app-c-api` resolves as an audience value that Application C can
validate.

**`domain-c-test-cli`** — a lab convenience so `scripts\get-token.ps1` can obtain a token for a *user*
of this domain without a browser. It carries both a realm-roles mapper and the audience mapper. Using
the deprecated direct-grant flow is acceptable for a local test script and nowhere else.

### Realm roles

| Role | Held by | Purpose |
|---|---|---|
| `app-c-api-reader` | the `app-a-federated-m2m` service account, and `frank` | read access to Application C's API |

### Protocol mappers

**`audience-app-c-api`** on `app-a-federated-m2m` (and on `domain-c-test-cli`):

```
Mapper type              Audience
Included Client Audience app-c-api
Add to access token      ON
```

Without it, tokens carry `"aud": "account"` and Application C — which validates
`audiences: [app-c-api]` — answers 401 with nothing helpful in the log.

### Users

| Username | Password | Roles | Purpose |
|---|---|---|---|
| `frank` | `Frank#2026` | `app-c-api-reader` | a human who exists **only** in this domain |
| `service-account-app-a-federated-m2m` | *(none)* | `app-c-api-reader` | Application A's machine identity here |

`frank` exists to make the isolation concrete rather than theoretical: his token opens Application C,
alice's does not, and neither user can be found in the other domain's console.

---

## Reproducing this by hand in the admin console

1. **Create the realm** — dropdown top-left → *Create realm* → `sso-domain-c`.
2. **Create the role** — *Realm roles* → *Create role* → `app-c-api-reader`.
3. **Create `app-c-api`** — *Clients* → *Create client* → client ID `app-c-api`, *Client
   authentication* On, and switch **off** standard flow, direct access grants and service accounts.
   Create it first, so the audience mappers below can reference it.
4. **Create `app-a-federated-m2m`** — *Client authentication* On, *Standard flow* **off**, *Direct
   access grants* **off**, *Service accounts roles* **On**. No redirect URIs.
   - *Credentials* tab → note the secret (it goes into `app-a/src/main/resources/application.yml`
     under the `app-c-m2m` registration).
   - *Service accounts roles* tab → *Assign role* → filter by realm roles → `app-c-api-reader`.
   - *Client scopes* → `app-a-federated-m2m-dedicated` → *Add mapper* → *By configuration* →
     **Audience** → *Included Client Audience* `app-c-api`, *Add to access token* On.
5. **Create `domain-c-test-cli`** — *Client authentication* On, *Direct access grants* **On**,
   everything else off. Add the same **Audience** mapper, plus a **User Realm Role** mapper with token
   claim name `realm_access.roles`, multivalued On.
6. **Create `frank`** — *Users* → *Add user*, *Email verified* On → *Credentials* → *Set password*,
   *Temporary* **Off** → *Role mapping* → assign `app-c-api-reader`.

Compare this list with [domain A's](../domain-a/README.md): no browser client, no login settings, no
redirect URIs. A domain that only serves machine callers needs strikingly little configuration.
