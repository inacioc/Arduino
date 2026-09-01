<#
.SYNOPSIS
    Fetches tokens straight from Keycloak and calls the APIs with them - no browser involved.

.DESCRIPTION
    The browser flows are the interesting part of SSO, but they are awkward to inspect. This script
    lets you hold a token in your hand: it prints the decoded payload, so you can see exactly which
    claims drive the authorization decisions, and then calls a protected endpoint with it.

    Two grants are demonstrated:

      client_credentials  the machine grant. An application authenticating as itself. This is what
                          Application A's scheduled jobs use.

      password            the "direct access grant". A user's password is posted straight to the
                          token endpoint, skipping the browser and therefore skipping SSO, MFA and
                          consent. Enabled here only because it makes testing easy; in production
                          this grant is deprecated and should stay switched off.

.PARAMETER Scenario
    Which check to run. 'all' runs every one and prints a summary.

.EXAMPLE
    .\get-token.ps1
    .\get-token.ps1 -Scenario cross-domain-rejected
    .\get-token.ps1 -Scenario decode -User alice
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'machine-to-b', 'machine-to-c', 'user-to-b', 'no-token',
                 'cross-domain-rejected', 'decode')]
    [string] $Scenario = 'all',

    [string] $User = 'alice',
    [string] $Password
)

$ErrorActionPreference = 'Continue'

$issuerA = 'http://localhost:8080/realms/sso-domain-a'
$issuerC = 'http://localhost:8090/realms/sso-domain-c'
$appB = 'http://localhost:8082'
$appC = 'http://localhost:8083'

$knownPasswords = @{
    alice = 'Alice#2026'
    bob   = 'Bob#2026'
    carol = 'Carol#2026'
    dave  = 'Dave#2026'
    frank = 'Frank#2026'
}

$results = [System.Collections.Generic.List[object]]::new()

function Add-Result {
    param([string] $Name, [bool] $Passed, [string] $Detail)
    $results.Add([pscustomobject]@{ Check = $Name; Passed = $Passed; Detail = $Detail })
    $colour = if ($Passed) { 'Green' } else { 'Red' }
    $mark = if ($Passed) { 'PASS' } else { 'FAIL' }
    Write-Host "  [$mark] $Detail" -ForegroundColor $colour
}

function Get-MachineToken {
    param([string] $Issuer, [string] $ClientId, [string] $ClientSecret)
    $response = Invoke-RestMethod -Method Post -TimeoutSec 15 `
        -Uri "$Issuer/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            grant_type    = 'client_credentials'
            client_id     = $ClientId
            client_secret = $ClientSecret
        }
    return $response.access_token
}

function Get-UserToken {
    param([string] $Issuer, [string] $ClientId, [string] $ClientSecret,
          [string] $Username, [string] $UserPassword)
    $response = Invoke-RestMethod -Method Post -TimeoutSec 15 `
        -Uri "$Issuer/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            grant_type    = 'password'
            client_id     = $ClientId
            client_secret = $ClientSecret
            username      = $Username
            password      = $UserPassword
            scope         = 'openid profile email'
        }
    return $response.access_token
}

function Show-TokenPayload {
    param([string] $Token, [string] $Label)

    # A JWT is three base64url segments joined by dots; the middle one is the readable payload.
    # Note there is no signature check here - this is decoding, not validating. Only the resource
    # server's verification of the signature makes a token trustworthy.
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    $json = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
    $claims = $json | ConvertFrom-Json

    Write-Host ''
    Write-Host "--- decoded token: $Label ---" -ForegroundColor Cyan
    Write-Host "  iss                : $($claims.iss)"
    Write-Host "  aud                : $($claims.aud -join ', ')"
    Write-Host "  azp                : $($claims.azp)"
    Write-Host "  sub                : $($claims.sub)"
    Write-Host "  preferred_username : $($claims.preferred_username)"
    Write-Host "  realm roles        : $($claims.realm_access.roles -join ', ')"
    $expiry = [DateTimeOffset]::FromUnixTimeSeconds($claims.exp).ToLocalTime()
    Write-Host "  exp                : $expiry"
    Write-Host ''
}

function Invoke-Api {
    param([string] $Url, [string] $Token)

    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }

    try {
        $response = Invoke-WebRequest -Uri $Url -Headers $headers -TimeoutSec 15 -UseBasicParsing
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $response.Content }
    }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Body = $_.Exception.Message }
    }
}

Write-Host ''
Write-Host '=== Token-level checks (no browser) ===' -ForegroundColor Cyan

# --- 1. Application identity against Application B, same SSO domain --------------------------
if ($Scenario -in 'all', 'machine-to-b') {
    Write-Host ''
    Write-Host 'Machine identity -> Application B (client_credentials, domain A)'
    try {
        $token = Get-MachineToken -Issuer $issuerA -ClientId 'app-a-m2m' -ClientSecret 'app-a-m2m-secret'
        Show-TokenPayload -Token $token -Label 'app-a-m2m from domain A'
        $result = Invoke-Api -Url "$appB/api/reports" -Token $token
        Add-Result 'machine-to-b' ($result.Status -eq 200) `
            "GET $appB/api/reports with app-a-m2m token -> HTTP $($result.Status) (expected 200)"
    }
    catch {
        Add-Result 'machine-to-b' $false "could not obtain token: $($_.Exception.Message)"
    }
}

# --- 2. Application identity against Application C, the other SSO domain ---------------------
if ($Scenario -in 'all', 'machine-to-c') {
    Write-Host ''
    Write-Host 'Machine identity -> Application C (client_credentials, domain C)'
    try {
        $token = Get-MachineToken -Issuer $issuerC -ClientId 'app-a-federated-m2m' `
            -ClientSecret 'app-a-federated-m2m-secret'
        Show-TokenPayload -Token $token -Label 'app-a-federated-m2m from domain C'
        $result = Invoke-Api -Url "$appC/api/inventory" -Token $token
        Add-Result 'machine-to-c' ($result.Status -eq 200) `
            "GET $appC/api/inventory with domain C token -> HTTP $($result.Status) (expected 200)"
    }
    catch {
        Add-Result 'machine-to-c' $false "could not obtain token: $($_.Exception.Message)"
    }
}

# --- 3. A human's token against Application B -----------------------------------------------
# alice holds app-b-user, so she is allowed. bob does not, so he is refused with 403 - the token
# is perfectly valid, he simply lacks the role. That difference between 401 and 403 is the whole
# distinction between authentication and authorization.
if ($Scenario -in 'all', 'user-to-b') {
    foreach ($name in @('alice', 'bob')) {
        Write-Host ''
        Write-Host "User identity ($name) -> Application B (direct access grant, domain A)"
        try {
            $token = Get-UserToken -Issuer $issuerA -ClientId 'app-a-web' `
                -ClientSecret 'app-a-web-secret' -Username $name `
                -UserPassword $knownPasswords[$name]
            Show-TokenPayload -Token $token -Label "$name from domain A"
            $result = Invoke-Api -Url "$appB/api/reports" -Token $token
            $expected = if ($name -eq 'alice') { 200 } else { 403 }
            Add-Result "user-to-b-$name" ($result.Status -eq $expected) `
                "GET $appB/api/reports as $name -> HTTP $($result.Status) (expected $expected)"
        }
        catch {
            Add-Result "user-to-b-$name" $false "could not obtain token: $($_.Exception.Message)"
        }
    }
}

# --- 4. No token at all ----------------------------------------------------------------------
if ($Scenario -in 'all', 'no-token') {
    Write-Host ''
    Write-Host 'No token -> Application B and Application C'
    $result = Invoke-Api -Url "$appB/api/reports" -Token $null
    Add-Result 'no-token-b' ($result.Status -eq 401) `
        "GET $appB/api/reports with no token -> HTTP $($result.Status) (expected 401)"
    $result = Invoke-Api -Url "$appC/api/inventory" -Token $null
    Add-Result 'no-token-c' ($result.Status -eq 401) `
        "GET $appC/api/inventory with no token -> HTTP $($result.Status) (expected 401)"
}

# --- 5. The point of two SSO domains ---------------------------------------------------------
# A token from domain A is refused by Application C: wrong issuer, and signed with a key
# Application C never fetches. Then the same call with a domain C token succeeds. This is what
# domain isolation actually means, as opposed to just having two folders of configuration.
if ($Scenario -in 'all', 'cross-domain-rejected') {
    Write-Host ''
    Write-Host 'Cross-domain isolation: a domain A token must NOT open Application C'
    try {
        $tokenA = Get-UserToken -Issuer $issuerA -ClientId 'app-a-web' `
            -ClientSecret 'app-a-web-secret' -Username 'alice' `
            -UserPassword $knownPasswords['alice']
        $result = Invoke-Api -Url "$appC/api/inventory" -Token $tokenA
        Add-Result 'cross-domain-rejected' ($result.Status -eq 401) `
            "GET $appC/api/inventory with a DOMAIN A token -> HTTP $($result.Status) (expected 401)"

        $tokenC = Get-UserToken -Issuer $issuerC -ClientId 'domain-c-test-cli' `
            -ClientSecret 'domain-c-test-cli-secret' -Username 'frank' `
            -UserPassword $knownPasswords['frank']
        $result = Invoke-Api -Url "$appC/api/inventory" -Token $tokenC
        Add-Result 'domain-c-user-accepted' ($result.Status -eq 200) `
            "GET $appC/api/inventory with a DOMAIN C token (frank) -> HTTP $($result.Status) (expected 200)"
    }
    catch {
        Add-Result 'cross-domain-rejected' $false "could not obtain token: $($_.Exception.Message)"
    }
}

# --- 6. Just decode a user's token -----------------------------------------------------------
if ($Scenario -eq 'decode') {
    $pwd = if ($Password) { $Password } else { $knownPasswords[$User] }
    if (-not $pwd) { throw "No password known for '$User'; pass -Password." }

    if ($User -eq 'frank') {
        $token = Get-UserToken -Issuer $issuerC -ClientId 'domain-c-test-cli' `
            -ClientSecret 'domain-c-test-cli-secret' -Username $User -UserPassword $pwd
    }
    else {
        $token = Get-UserToken -Issuer $issuerA -ClientId 'app-a-web' `
            -ClientSecret 'app-a-web-secret' -Username $User -UserPassword $pwd
    }
    Show-TokenPayload -Token $token -Label $User
    Write-Host 'Raw token (paste into https://jwt.io to inspect the header and signature):'
    Write-Host $token -ForegroundColor DarkGray
    Write-Host ''
    exit 0
}

# --- Summary ----------------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Summary ===' -ForegroundColor Cyan
$results | Format-Table -AutoSize

$failed = @($results | Where-Object { -not $_.Passed })
if ($failed.Count -eq 0) {
    Write-Host "All $($results.Count) checks passed." -ForegroundColor Green
    Write-Host ''
    exit 0
}

Write-Host "$($failed.Count) of $($results.Count) checks failed. See docs\troubleshooting.md." -ForegroundColor Red
Write-Host ''
exit 1
