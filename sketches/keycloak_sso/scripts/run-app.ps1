<#
.SYNOPSIS
    Starts one of the three applications in the foreground.

.DESCRIPTION
    Each application runs in its own terminal so its log is readable on its own. Application A's log
    is the interesting one: the scheduled machine-to-machine results are printed there.

    Both Keycloak instances must already be running - the applications fetch the OpenID Connect
    discovery documents at startup and will fail fast if an issuer is unreachable. Run
    scripts\verify-keycloak.ps1 first if unsure.

.PARAMETER App
    a, b or c.

.EXAMPLE
    .\run-app.ps1 a
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('a', 'b', 'c')]
    [string] $App
)

$ErrorActionPreference = 'Stop'

$labRoot = Split-Path -Parent $PSScriptRoot

$ports = @{ a = 8081; b = 8082; c = 8083 }
$notes = @{
    a = 'Open http://localhost:8081/ and sign in. Watch this window for the scheduled M2M calls.'
    b = 'Open http://localhost:8082/ - or reach it from Application A to experience SSO.'
    c = 'API only, no pages. Called by Application A''s scheduler and by scripts\get-token.ps1.'
}

Write-Host ''
Write-Host "=== Application $($App.ToUpper()) on port $($ports[$App]) ===" -ForegroundColor Cyan
Write-Host $notes[$App]
Write-Host ''

# -pl selects the module; the reactor still resolves the parent POM for dependency management.
& mvn -f (Join-Path $labRoot 'pom.xml') -pl "app-$App" spring-boot:run
