<#
.SYNOPSIS
    Starts SSO domain A: Keycloak on http://localhost:8080, realm 'sso-domain-a'.

.DESCRIPTION
    This is the identity provider shared by Application A and Application B. Because they are two
    clients of this one realm, a user who logs in to either is already logged in to the other -
    that is the whole SSO effect, and it is produced here rather than in the applications.

    Leave this window open; Keycloak runs in the foreground. Ctrl+C stops it.

.PARAMETER KeycloakHome
    Installation directory. Default C:\Apps\keycloak-a, as created by scripts\install-keycloak.ps1.

.PARAMETER Fresh
    Wipe the dev-mode database first, so the realm JSON is imported again from scratch. Needed
    after editing realm-sso-domain-a.json, because --import-realm skips realms that already exist.
#>
[CmdletBinding()]
param(
    [string] $KeycloakHome = 'C:\Apps\keycloak-a',
    [switch] $Fresh
)

$ErrorActionPreference = 'Stop'

$kc = Join-Path $KeycloakHome 'bin\kc.bat'
if (-not (Test-Path $kc)) {
    throw "Keycloak not found at $KeycloakHome. Run scripts\install-keycloak.ps1 first."
}

if ($Fresh) {
    $dataDir = Join-Path $KeycloakHome 'data\h2'
    if (Test-Path $dataDir) {
        Write-Host 'Removing the dev database so the realm is re-imported...' -ForegroundColor Yellow
        Remove-Item -Recurse -Force $dataDir
    }
}

# Keycloak 26 renamed these from the old KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD. They only take
# effect on the very first start, when there is no admin user yet.
$env:KC_BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = 'admin'

Write-Host ''
Write-Host '=== SSO DOMAIN A ===' -ForegroundColor Blue
Write-Host 'Admin console : http://localhost:8080  (admin / admin)'
Write-Host 'Realm         : sso-domain-a'
Write-Host 'Issuer        : http://localhost:8080/realms/sso-domain-a'
Write-Host 'Used by       : Application A (:8081) and Application B (:8082)'
Write-Host ''

# --http-management-port is explicit even though 9000 is the default, so that the difference with
# domain C's 9090 is visible. Keycloak 26 always binds a management port for health/metrics, and
# two instances would otherwise collide on it.
& $kc start-dev `
    --http-port=8080 `
    --http-management-port=9000 `
    --import-realm
