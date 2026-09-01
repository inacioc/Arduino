<#
.SYNOPSIS
    Starts SSO domain C: a second, independent Keycloak on http://localhost:8090, realm
    'sso-domain-c'.

.DESCRIPTION
    A completely separate identity provider: its own installation directory, its own database, its
    own signing keys, its own users. It knows nothing about domain A, and no trust relationship
    exists between them. Application A can call Application C only because an administrator of
    *this* domain issued it a client id and secret of its own.

    Leave this window open; Keycloak runs in the foreground. Ctrl+C stops it.

.PARAMETER KeycloakHome
    Installation directory. Default C:\Apps\keycloak-c.

.PARAMETER Fresh
    Wipe the dev-mode database first so the realm JSON is imported again.
#>
[CmdletBinding()]
param(
    [string] $KeycloakHome = 'C:\Apps\keycloak-c',
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

$env:KC_BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = 'admin'

Write-Host ''
Write-Host '=== SSO DOMAIN C ===' -ForegroundColor DarkYellow
Write-Host 'Admin console : http://localhost:8090  (admin / admin)'
Write-Host 'Realm         : sso-domain-c'
Write-Host 'Issuer        : http://localhost:8090/realms/sso-domain-c'
Write-Host 'Used by       : Application C (:8083), and by Application A as a foreign client'
Write-Host ''

# --http-management-port=9090 is MANDATORY here, not cosmetic. Keycloak 26 always binds a
# management interface, default 9000, which domain A already holds. Without this override the
# second instance fails to start with a port-already-in-use error that never mentions 9000.
& $kc start-dev `
    --http-port=8090 `
    --http-management-port=9090 `
    --import-realm
