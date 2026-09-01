<#
.SYNOPSIS
    Installs two independent Keycloak instances on this machine - one per SSO domain - and stages
    the realm import files.

.DESCRIPTION
    Downloads the Keycloak ZIP distribution once, then extracts it twice. Two separate copies is
    the point: each instance keeps its own conf/, data/ and dev-mode H2 database, so the two SSO
    domains really are two independent identity providers rather than one server wearing two hats.

    Layout produced (nothing is installed system-wide, nothing touches the registry):

        <InstallRoot>\keycloak-a\   -> SSO domain A, HTTP 8080, management 9000
        <InstallRoot>\keycloak-c\   -> SSO domain C, HTTP 8090, management 9090

.PARAMETER InstallRoot
    Where the two instances go. Default C:\Apps.

.PARAMETER Version
    Keycloak version to install. 26.7.0 is the version this lab was verified against.

.PARAMETER Force
    Delete and reinstall an instance that is already present.

.EXAMPLE
    .\install-keycloak.ps1
#>
[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Apps',
    [string] $Version = '26.7.0',
    [switch] $Force
)

$ErrorActionPreference = 'Stop'

$labRoot = Split-Path -Parent $PSScriptRoot
$zipName = "keycloak-$Version.zip"
$zipPath = Join-Path $env:TEMP $zipName
$downloadUrl = "https://github.com/keycloak/keycloak/releases/download/$Version/$zipName"

Write-Host ''
Write-Host '=== Keycloak SSO lab: installing two identity providers ===' -ForegroundColor Cyan
Write-Host ''

# --- Java check -------------------------------------------------------------------------------
# Keycloak 26.7 supports OpenJDK 17, 21 and 25.
if (-not $env:JAVA_HOME) {
    Write-Warning 'JAVA_HOME is not set. Keycloak needs it to find a JDK.'
}
else {
    Write-Host "JAVA_HOME : $env:JAVA_HOME"
}
# 'java --version' (two dashes) prints to stdout; the old '-version' prints to stderr, which
# Windows PowerShell 5.1 turns into an error record.
$javaVersion = (& java --version | Select-Object -First 1)
Write-Host "java      : $javaVersion"
Write-Host ''

# --- Download ---------------------------------------------------------------------------------
if (Test-Path $zipPath) {
    Write-Host "Using already downloaded $zipPath"
}
else {
    Write-Host "Downloading $downloadUrl"
    Write-Host '(about 250 MB, this is the only download needed)'
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -UseBasicParsing
    Write-Host 'Download complete.'
}
Write-Host ''

# --- Extract twice ----------------------------------------------------------------------------
$instances = @(
    [pscustomobject]@{ Name = 'keycloak-a'; Domain = 'domain-a'; Realm = 'sso-domain-a'; Http = 8080; Mgmt = 9000 }
    [pscustomobject]@{ Name = 'keycloak-c'; Domain = 'domain-c'; Realm = 'sso-domain-c'; Http = 8090; Mgmt = 9090 }
)

if (-not (Test-Path $InstallRoot)) {
    New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
}

foreach ($instance in $instances) {
    $target = Join-Path $InstallRoot $instance.Name

    if ((Test-Path $target) -and -not $Force) {
        Write-Host "$($instance.Name): already present at $target (use -Force to reinstall)" -ForegroundColor Yellow
    }
    else {
        if (Test-Path $target) {
            Write-Host "$($instance.Name): removing existing installation"
            Remove-Item -Recurse -Force $target
        }

        Write-Host "$($instance.Name): extracting to $target"
        $staging = Join-Path $env:TEMP "kc-staging-$($instance.Name)"
        if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
        Expand-Archive -Path $zipPath -DestinationPath $staging -Force

        # The archive contains a single keycloak-<version> folder; lift it up so paths stay short.
        $extracted = Join-Path $staging "keycloak-$Version"
        Move-Item -Path $extracted -Destination $target
        Remove-Item -Recurse -Force $staging
    }

    # --- Stage the realm import file ----------------------------------------------------------
    # start-dev --import-realm reads every JSON in <home>\data\import.
    $importDir = Join-Path $target 'data\import'
    if (-not (Test-Path $importDir)) {
        New-Item -ItemType Directory -Path $importDir -Force | Out-Null
    }

    $realmSource = Join-Path $labRoot "keycloak\$($instance.Domain)\realm-$($instance.Realm).json"
    if (-not (Test-Path $realmSource)) {
        throw "Realm file not found: $realmSource"
    }
    Copy-Item -Path $realmSource -Destination $importDir -Force
    Write-Host "$($instance.Name): staged realm '$($instance.Realm)' into data\import" -ForegroundColor Green
}

Write-Host ''
Write-Host '=== Done ===' -ForegroundColor Cyan
Write-Host ''
Write-Host 'Next, in two separate terminals:'
Write-Host "  .\keycloak\domain-a\start-keycloak-a.ps1     # http://localhost:8080"
Write-Host "  .\keycloak\domain-c\start-keycloak-c.ps1     # http://localhost:8090"
Write-Host ''
Write-Host 'Then verify both issuers respond before starting the Java applications:'
Write-Host '  .\scripts\verify-keycloak.ps1'
Write-Host ''
