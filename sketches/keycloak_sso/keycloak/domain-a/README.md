# SSO domain A

The identity provider shared by **Application A** and **Application B**. Sharing one realm between
two clients is the entire mechanism behind single sign-on here — there is no other trick.

| | |
|---|---|
| Instance | `C:\Apps\keycloak-a` (its own installation and database) |
| Admin console | <http://localhost:8080> — `admin` / `admin` |
| Realm | `sso-domain-a` |
| Issuer | `http://localhost:8080/realms/sso-domain-a` |
| Management port | 9000 (health/metrics; Keycloak 26 always binds one) |
| Start | `.\start-keycloak-a.ps1` (add `-Fresh` to force a realm re-import) |

Discovery document, useful for checking everything by hand:
<http://localhost:8080/realms/sso-domain-a/.well-known/openid-configuration>

---

## What is in the realm, and why

### Clients

| clientId | Confidential | Standard flow | Direct grant | Service account | Secret |
|---|---|---|---|---|---|
| `app-a-web` | yes | **on** | on *(testing only)* | off | `app-a-web-secret` |
| `app-b-web` | yes | **on** | on *(testing only)* | off | `app-b-web-secret` |
| `app-a-m2m` | yes | off | off | **on** | `app-a-m2m-secret` |
| `app-b-api` | yes | off | off | off | *(never used)* |

**`app-a-web` and `app-b-web`** are the two browser clients. They are separate clients with separate
secrets — Application A cannot use Application B's credentials and vice versa. What they share is the
realm, so a user who authenticates for one already has a Keycloak session that satisfies the other.

Login settings on each:

```
Root URL                      http://localhost:8081        (8082 for app-b-web)
Valid redirect URIs           http://localhost:8081/login/oauth2/code/keycloak-a
                              http://localhost:9876/callback     (app-a-web only - see below)
Valid post logout redirect    http://localhost:8081/
Web origins                   http://localhost:8081
```

The redirect URI must match **exactly**. Note the path segment `keycloak-a`: Spring derives it from
the *registration id* in `application.yml`, not from the client id.

`http://localhost:9876/callback` on `app-a-web` is for the [Bruno](../../bruno.md) API client, which
can drive the authorization-code flow for manual testing. Bruno never navigates to that URL — it
intercepts the redirect internally — but Keycloak validates `redirect_uri` against this list
regardless, so the entry has to exist. It grants the applications nothing; remove it if you do not
use Bruno.

**`app-a-m2m`** is Application A's own identity — what it uses when no user is involved. Standard flow
and direct access grants are deliberately **off**: no human can ever log in as this client. Only
"Service accounts roles" is enabled, which is Keycloak's name for the `client_credentials` grant.

**`app-b-api`** has no flows at all. It exists so that the string `app-b-api` resolves to a real
client, which lets the audience mapper reference it and Application B validate against it. Think of
it as the name of the protected resource rather than an application.

### Realm roles

| Role | Held by | Purpose |
|---|---|---|
| `app-a-user` | alice, bob, dave | interactive access to Application A |
| `app-b-user` | alice, carol, dave | interactive access to Application B, and to its API when relayed |
| `app-b-api-reader` | the `app-a-m2m` service account only | machine read access to Application B's API |
| `sso-admin` | dave | spare role for role-gated views |

Humans and machines get **different** roles on purpose. `app-a-m2m` holds `app-b-api-reader` and
nothing else, so a leaked machine secret cannot be used to browse Application A.

### Protocol mappers — the two that are easy to miss

Both of these cause silent failures when absent, and neither is present by default.

**1. `realm-roles-in-id-token`** on `app-a-web` and `app-b-web`

```
Mapper type      User Realm Role
Token Claim Name realm_access.roles
Multivalued      on
Add to ID token  ON        ← the part Keycloak does not do by default
Add to access token  on
```

Keycloak puts realm roles in the **access** token; `oauth2Login` reads the **ID** token. Without this,
a user logs in perfectly and receives zero authorities, so every `/ui/**` page answers 403
immediately after a successful login.

**2. `audience-app-b-api`** on `app-a-m2m` and `app-a-web`

```
Mapper type              Audience
Included Client Audience app-b-api
Add to access token      ON
```

Keycloak issues `"aud": "account"` by default. Application B validates
`audiences: [app-b-api]`, so without this mapper every API call is a bare 401 with nothing useful
logged. It is on `app-a-web` too, so a *user's* token also works against Application B's API — which
is what makes the token-relay flow possible.

### Users

| Username | Password | Roles | What it demonstrates |
|---|---|---|---|
| `alice` | `Alice#2026` | `app-a-user`, `app-b-user` | SSO into both applications |
| `bob` | `Bob#2026` | `app-a-user` | authenticated by the domain, 403 in Application B |
| `carol` | `Carol#2026` | `app-b-user` | the mirror case — 403 in Application A |
| `dave` | `Dave#2026` | `app-a-user`, `app-b-user`, `sso-admin` | an extra role on top of both |
| `service-account-app-a-m2m` | *(none)* | `app-b-api-reader` | the machine identity |

Passwords are imported with `"temporary": false`, so no "update password" screen blocks the first
login. The service-account user is linked to its client with `serviceAccountClientId` — note the
field name; `serviceAccountClientLink` does not exist and aborts the whole import.

> Direct access grants are enabled on the two web clients purely so `scripts\get-token.ps1` can fetch
> user tokens without a browser. That grant posts a password straight to the token endpoint, bypassing
> SSO, MFA and consent. It is deprecated for real use — leave it off in production.

---

## Reproducing this by hand in the admin console

Worth doing once instead of importing, if the goal is to understand the settings.

1. **Create the realm** — dropdown top-left → *Create realm* → name `sso-domain-a` → *Create*.
2. **Create the realm roles** — *Realm roles* → *Create role*, four times:
   `app-a-user`, `app-b-user`, `app-b-api-reader`, `sso-admin`.
3. **Create `app-a-web`** — *Clients* → *Create client*.
   - Client ID `app-a-web`, *Next*.
   - Capability config: *Client authentication* **On** (this is what "confidential" means),
     *Standard flow* on, *Direct access grants* on, *Service accounts roles* off. *Next*.
   - Login settings: root URL `http://localhost:8081`, valid redirect URI
     `http://localhost:8081/login/oauth2/code/keycloak-a` (add a second,
     `http://localhost:9876/callback`, if you plan to use the Bruno collection), valid post logout
     redirect `http://localhost:8081/`, web origin `http://localhost:8081`. *Save*.
   - *Credentials* tab → copy the secret, or use *Regenerate* and paste your own value into
     `app-a/src/main/resources/application.yml`.
   - *Client scopes* tab → `app-a-web-dedicated` → *Add mapper* → *By configuration* →
     **User Realm Role**: name `realm-roles-in-id-token`, token claim name `realm_access.roles`,
     multivalued **On**, *Add to ID token* **On**.
   - Same dedicated scope → *Add mapper* → **Audience**: name `audience-app-b-api`,
     *Included Client Audience* `app-b-api`, *Add to access token* **On**.
4. **Create `app-b-web`** — identical, with `8082` everywhere. Keep the redirect path
   `/login/oauth2/code/keycloak-a`: it comes from the registration id, which is the same in both
   applications.
5. **Create `app-b-api`** — client ID `app-b-api`, *Client authentication* On, and turn **off**
   standard flow, direct access grants and service accounts. It only needs to exist.
6. **Create `app-a-m2m`** — *Client authentication* On, *Standard flow* **off**, *Direct access
   grants* **off**, *Service accounts roles* **On**. No redirect URIs.
   - *Credentials* tab → note the secret.
   - *Service accounts roles* tab → *Assign role* → filter by realm roles → `app-b-api-reader`.
   - *Client scopes* → `app-a-m2m-dedicated` → *Add mapper* → **Audience** → `app-b-api`.
7. **Create the users** — *Users* → *Add user* (set *Email verified* On), then *Credentials* →
   *Set password* with *Temporary* **Off**, then *Role mapping* → *Assign role* → filter by realm
   roles.

Order matters in one place: create `app-b-api` **before** the audience mappers that reference it,
otherwise the client picker has nothing to offer.

## Exporting your changes back to JSON

Clicked something useful and want to keep it? A realm export from the running server produces a file
you can commit:

```powershell
# stop the server first
& C:\Apps\keycloak-a\bin\kc.bat export --dir C:\temp\kc-export --realm sso-domain-a
```

Beware: exports do **not** include client secrets or user passwords, so those must be re-added by
hand before the file can be re-imported into a working lab. That is why the committed
`realm-sso-domain-a.json` is hand-maintained rather than a raw export.
