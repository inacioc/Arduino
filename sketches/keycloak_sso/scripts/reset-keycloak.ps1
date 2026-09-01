<#
.SYNOPSIS
    Wipes the dev databases of both Keycloak instances and re-stages the realm JSON files, so the
    next start imports them from scratch.

.DESCRIPTION
    Read this if you edited a realm JSON and nothing changed.

    'start-dev --import-realm' deliberately SKIPS a realm that already exists, to avoid destroying
    state between restarts. It does so silently as far as your edit is concerned: the server starts
    fine, reports no error, and keeps serving the old configuration. That is the single most
    confusing failure mode in this lab.

    This script removes the dev-mode H2 database from each instance, which is where the imported
    realms live, and copies the current realm JSON files back into data\import.

    Stop both Keycloak instances before running it.

.PARAMETER InstallRoot
    Where the instances were installed. Default C:\Apps.

.EXAMPLE
    .\reset-keycloak.ps1
#>
[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Apps'
)

$ErrorActionPreference = 'Stop'

$labRoot = Split-Path -Parent $PSScriptRoot

$instances = @(
    [pscustomobject]@{ Name = 'keycloak-a'; Domain = 'domain-a'; Realm = 'sso-domain-a'; Port = 8080 }
    [pscustomobject]@{ Name = 'keycloak-c'; Domain = 'domain-c'; Realm = 'sso-domain-c'; Port = 8090 }
)

Write-Host ''
Write-Host '=== Resetting both SSO domains ===' -ForegroundColor Cyan
Write-Host ''

# Refuse to run against a live server: deleting its database underneath it leaves a mess.
foreach ($instance in $instances) {
    $listener = Get-NetTCPConnection -LocalPort $instance.Port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        throw "Keycloak still appears to be running on port $($instance.Port). Stop it (Ctrl+C in its window) and run this again."
    }
}

foreach ($instance in $instances) {
    $home = Join-Path $InstallRoot $instance.Name
    if (-not (Test-Path $home)) {
        Write-Host "$($instance.Name): not installed at $home - skipping" -ForegroundColor Yellow
        continue
    }

    $dbDir = Join-Path $home 'data\h2'
    if (Test-Path $dbDir) {
        Remove-Item -Recurse -Force $dbDir
        Write-Host "$($instance.Name): dev database removed" -ForegroundColor Green
    }
    else {
        Write-Host "$($instance.Name): no dev database to remove"
    }

    $importDir = Join-Path $home 'data\import'
    if (-not (Test-Path $importDir)) {
        New-Item -ItemType Directory -Path $importDir -Force | Out-Null
    }
    $realmSource = Join-Path $labRoot "keycloak\$($instance.Domain)\realm-$($instance.Realm).json"
    Copy-Item -Path $realmSource -Destination $importDir -Force
    Write-Host "$($instance.Name): re-staged realm '$($instance.Realm)'" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Done. Both realms will be imported fresh on the next start.'
Write-Host 'Note that the admin user is recreated too, so admin / admin applies again.'
Write-Host ''
