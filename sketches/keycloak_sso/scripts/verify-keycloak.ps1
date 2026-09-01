<#
.SYNOPSIS
    Checks that both SSO domains are up and correctly provisioned, before any Java is started.

.DESCRIPTION
    Worth running first every time. Nearly every confusing failure later on - endless redirect
    loops, 401s with no explanation, applications refusing to start - traces back to something this
    script would have caught in two seconds: a realm that did not import, a Keycloak that is not
    running, or an issuer URL that does not match what the applications expect.

    Verifies, per domain:
      * the OpenID Connect discovery document is reachable
      * the 'issuer' it reports is exactly what the applications are configured to trust
      * the expected clients exist (by asking for a token, which also proves the secrets match)
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Test-Discovery {
    param([string] $Label, [string] $Issuer)

    Write-Host ''
    Write-Host "--- $Label ---" -ForegroundColor Cyan
    $url = "$Issuer/.well-known/openid-configuration"

    try {
        $doc = Invoke-RestMethod -Uri $url -TimeoutSec 10
    }
    catch {
        Write-Host "  UNREACHABLE  $url" -ForegroundColor Red
        Write-Host "               $($_.Exception.Message)"
        Write-Host '               Is this Keycloak instance running, and did the realm import?'
        return $false
    }

    Write-Host "  discovery    OK" -ForegroundColor Green
    if ($doc.issuer -eq $Issuer) {
        Write-Host "  issuer       $($doc.issuer)" -ForegroundColor Green
    }
    else {
        # A mismatch here breaks token validation everywhere, because the 'iss' claim in issued
        # tokens will not equal what the resource servers were told to expect.
        Write-Host "  issuer       MISMATCH" -ForegroundColor Red
        Write-Host "               reported : $($doc.issuer)"
        Write-Host "               expected : $Issuer"
        return $false
    }
    Write-Host "  token url    $($doc.token_endpoint)"
    Write-Host "  jwks url     $($doc.jwks_uri)"
    return $true
}

function Test-ClientCredentials {
    param([string] $Issuer, [string] $ClientId, [string] $ClientSecret)

    try {
        $response = Invoke-RestMethod -Method Post -TimeoutSec 10 `
            -Uri "$Issuer/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{
                grant_type    = 'client_credentials'
                client_id     = $ClientId
                client_secret = $ClientSecret
            }
        if ($response.access_token) {
            Write-Host "  client       $ClientId : token issued OK" -ForegroundColor Green
            return $true
        }
        Write-Host "  client       $ClientId : no access_token in response" -ForegroundColor Red
        return $false
    }
    catch {
        Write-Host "  client       $ClientId : FAILED - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host '               Wrong secret, service accounts disabled, or client missing.'
        return $false
    }
}

$issuerA = 'http://localhost:8080/realms/sso-domain-a'
$issuerC = 'http://localhost:8090/realms/sso-domain-c'

Write-Host ''
Write-Host '=== Verifying both SSO domains ===' -ForegroundColor Cyan

$ok = $true

if (Test-Discovery -Label 'SSO domain A (Application A + Application B)' -Issuer $issuerA) {
    if (-not (Test-ClientCredentials -Issuer $issuerA -ClientId 'app-a-m2m' -ClientSecret 'app-a-m2m-secret')) { $ok = $false }
}
else { $ok = $false }

if (Test-Discovery -Label 'SSO domain C (Application C)' -Issuer $issuerC) {
    if (-not (Test-ClientCredentials -Issuer $issuerC -ClientId 'app-a-federated-m2m' -ClientSecret 'app-a-federated-m2m-secret')) { $ok = $false }
}
else { $ok = $false }

Write-Host ''
if ($ok) {
    Write-Host 'Both SSO domains are ready. You can start the three applications.' -ForegroundColor Green
    Write-Host ''
    exit 0
}

Write-Host 'Something is not ready yet - see above. Do not start the applications until this passes;' -ForegroundColor Red
Write-Host 'they read the discovery documents at startup and will fail if an issuer is unreachable.' -ForegroundColor Red
Write-Host ''
exit 1
