# Spring Boot 3/4 OAuth2/OIDC Authentication Flow Debugging Guide

## Goal
Trace the complete authentication flow and verify that **tokens are being properly exchanged and stored** in both scenarios (Bruno vs Frontend).

---

## Part 1: Enable Debug Logging

### Step 1: Configure application.yml

Add this to `src/main/resources/application.yml`:

```yaml
logging:
  level:
    root: INFO
    
    # Spring Security - All authentication decisions
    org.springframework.security: DEBUG
    
    # Spring Security OAuth2 Client - Token exchange details
    org.springframework.security.oauth2.client: DEBUG
    
    # Spring Security OAuth2 Resource Server - Token validation
    org.springframework.security.oauth2.server.resource: DEBUG
    
    # Spring Web - HTTP request details
    org.springframework.web: DEBUG
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: DEBUG
    
    # Your application
    com.example: DEBUG
    
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

spring:
  application:
    name: my-app
  
  security:
    oauth2:
      # ... rest of your OAuth2 config
```

### Step 2: Restart Spring Boot

```bash
mvn spring-boot:run
```

You should see **lots of debug output**. This is good!

---

## Part 2: Understanding the Log Output

### What to Look For

When a user logs in, you'll see logs like:

```
[main] DEBUG org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter 
  - Redirecting to authorization server with OAuth2 AuthorizationRequest: 
    clientId=my-app
    redirectUri=http://localhost:8081/login/oauth2/code/keycloak
    scopes=[openid, profile, email]
```

This means: **Step 1 - User is being redirected to Keycloak**

Then:

```
[main] DEBUG org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider 
  - Authenticating OAuth2 authentication request: 
    clientRegistration=ClientRegistration{clientId='my-app', ...}
```

This means: **Step 2 - Backend is exchanging the code for tokens**

Then:

```
[main] DEBUG org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider 
  - Token exchange completed. 
    ID Token: eyJhbGci...
    Access Token: eyJhbGci...
```

This means: **Step 3 - Tokens successfully received and stored**

---

## Part 3: Create a Custom Debug Interceptor

This intercepts **every HTTP request** and logs what authentication is present.

### Create: `src/main/java/com/example/config/DebugSecurityInterceptor.java`

```java
package com.example.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;

@Component
public class DebugSecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        
        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📍 INCOMING REQUEST");
        System.out.println("=".repeat(80));
        System.out.println("Method: " + method);
        System.out.println("Path: " + path + queryString);
        System.out.println("Remote User: " + request.getRemoteUser());
        
        // Check what's in the session
        System.out.println("\n🔑 SECURITY CONTEXT:");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null) {
            System.out.println("  ❌ Authentication: NULL");
            System.out.println("  ❌ User is NOT authenticated");
        } else {
            System.out.println("  ✅ Authentication: " + auth.getClass().getSimpleName());
            System.out.println("  ✅ Authenticated: " + auth.isAuthenticated());
            System.out.println("  ✅ Principal: " + auth.getPrincipal());
            
            if (auth.getPrincipal() instanceof OidcUser) {
                OidcUser user = (OidcUser) auth.getPrincipal();
                System.out.println("  ✅ OIDC User:");
                System.out.println("      - Username: " + user.getPreferredUsername());
                System.out.println("      - Email: " + user.getEmail());
                System.out.println("      - ID Token Exp: " + user.getIdToken().getExpiresAt());
                System.out.println("      - ID Token Claims: " + user.getIdToken().getClaims().keySet());
            }
            
            System.out.println("  Authorities: " + auth.getAuthorities());
        }
        
        // Check session
        System.out.println("\n📦 SESSION:");
        System.out.println("  Session ID: " + request.getSession().getId());
        System.out.println("  Session Creation Time: " + request.getSession().getCreationTime());
        System.out.println("  Session is New: " + request.getSession(false) == null);
        
        // Check cookies
        System.out.println("\n🍪 COOKIES:");
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (cookie.getName().contains("SESSION") || cookie.getName().contains("XSRF")) {
                    System.out.println("  - " + cookie.getName() + " = " + cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())) + "...");
                }
            }
        } else {
            System.out.println("  ❌ No cookies!");
        }
        
        System.out.println("=".repeat(80) + "\n");
        
        return true;
    }
}
```

### Register the Interceptor: Update your `SecurityConfig.java`

```java
package com.example.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private DebugSecurityInterceptor debugInterceptor;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2Login()
                .defaultSuccessUrl("/", true)
            .and()
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .logout()
                .logoutSuccessUrl("/");
        
        return http.build();
    }

    // ← Add this to register the interceptor
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(debugInterceptor);
            }
        };
    }
}
```

---

## Part 4: Create an Authentication Status Endpoint

This endpoint shows you **exactly what the backend knows about the current user**.

### Create: `src/main/java/com/example/controller/DebugController.java`

```java
package com.example.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@RestController
public class DebugController {

    /**
     * GET /debug/auth-status
     * Shows complete authentication status
     */
    @GetMapping("/debug/auth-status")
    public Map<String, Object> authStatus(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 1. Current Authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null) {
            result.put("authenticated", false);
            result.put("message", "No authentication found in SecurityContext");
            return result;
        }
        
        result.put("authenticated", auth.isAuthenticated());
        result.put("authenticationClass", auth.getClass().getSimpleName());
        result.put("principal", auth.getPrincipal().getClass().getSimpleName());
        
        // 2. OIDC User Details
        if (auth.getPrincipal() instanceof OidcUser) {
            OidcUser user = (OidcUser) auth.getPrincipal();
            
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("username", user.getPreferredUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("name", user.getFullName());
            userInfo.put("sub", user.getSubject());
            
            // ID Token info
            Map<String, Object> idTokenInfo = new LinkedHashMap<>();
            idTokenInfo.put("expiresAt", user.getIdToken().getExpiresAt());
            idTokenInfo.put("issuedAt", user.getIdToken().getIssuedAt());
            idTokenInfo.put("issuer", user.getIdToken().getIssuer());
            idTokenInfo.put("claims", user.getIdToken().getClaims().keySet());
            
            userInfo.put("idToken", idTokenInfo);
            result.put("user", userInfo);
        }
        
        // 3. Session info
        HttpSession session = request.getSession(false);
        if (session != null) {
            result.put("session", Map.of(
                "id", session.getId(),
                "createdTime", new Date(session.getCreationTime()),
                "lastAccessedTime", new Date(session.getLastAccessedTime()),
                "isNew", session.isNew(),
                "attributeNames", Collections.list(session.getAttributeNames())
            ));
        }
        
        // 4. Cookies
        List<Map<String, String>> cookies = new ArrayList<>();
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (cookie.getName().contains("SESSION") || cookie.getName().contains("XSRF")) {
                    cookies.add(Map.of(
                        "name", cookie.getName(),
                        "value", cookie.getValue().substring(0, Math.min(30, cookie.getValue().length())) + "...",
                        "secure", String.valueOf(cookie.getSecure()),
                        "httpOnly", String.valueOf(cookie.isHttpOnly()),
                        "maxAge", String.valueOf(cookie.getMaxAge())
                    ));
                }
            }
        }
        result.put("cookies", cookies);
        
        // 5. OAuth2 Token (if using resource server)
        if (auth instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) auth;
            result.put("oauth2", Map.of(
                "authorizedClientName", oauth2Token.getAuthorizedClientName(),
                "attributes", oauth2Token.getPrincipal().getAttributes().keySet()
            ));
        }
        
        result.put("timestamp", new Date());
        
        return result;
    }

    /**
     * GET /debug/request-info
     * Shows what the current request contains
     */
    @GetMapping("/debug/request-info")
    public Map<String, Object> requestInfo(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        result.put("remoteUser", request.getRemoteUser());
        result.put("userPrincipal", request.getUserPrincipal() != null ? 
            request.getUserPrincipal().getName() : null);
        
        // Show headers
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!name.toLowerCase().contains("user-agent")) {
                headers.put(name, request.getHeader(name).substring(0, Math.min(100, request.getHeader(name).length())));
            }
        });
        result.put("headers", headers);
        
        result.put("method", request.getMethod());
        result.put("path", request.getRequestURI());
        result.put("queryString", request.getQueryString());
        
        return result;
    }
}
```

---

## Part 5: How to Use These Debugging Tools

### Step 1: Start Your App with Debug Logging

```bash
mvn spring-boot:run
```

### Step 2: Login via Frontend

1. Visit: `http://localhost:8081/`
2. Click "Login with Keycloak"
3. Authenticate

**Watch the console** - You'll see detailed logs of:
- Token exchange happening
- Tokens being received
- Authentication being set in SecurityContext

### Step 3: Call the Debug Endpoint from Frontend

```bash
# In your browser, visit:
http://localhost:8081/debug/auth-status
```

You'll get JSON showing:
```json
{
  "authenticated": true,
  "authenticationClass": "OAuth2AuthenticationToken",
  "user": {
    "username": "testuser",
    "email": "test@example.com",
    "idToken": {
      "expiresAt": "2024-04-20T15:30:00Z",
      "claims": ["sub", "email", "name", ...]
    }
  },
  "session": {
    "id": "abc123",
    "attributeNames": ["SPRING_SECURITY_CONTEXT", ...]
  },
  "cookies": [
    {
      "name": "JSESSIONID",
      "value": "abc123...",
      "secure": true,
      "httpOnly": true
    }
  ]
}
```

### Step 4: Try the Protected Endpoint from Frontend

Visit (in browser):
```
http://localhost:8081/api/protected-resource
```

**Check the console output** from your `DebugSecurityInterceptor`:
```
================================================================================
📍 INCOMING REQUEST
================================================================================
Method: GET
Path: /api/protected-resource
Remote User: testuser

🔑 SECURITY CONTEXT:
  ✅ Authentication: OAuth2AuthenticationToken
  ✅ Authenticated: true
  ✅ Principal: OidcUser
  ✅ OIDC User:
      - Username: testuser
      - Email: test@example.com
================================================================================
```

If you see `❌ Authentication: NULL`, **that's your problem!**

---

## Part 6: Compare Bruno vs Frontend

### With Bruno

**Step 1:** Get tokens from Keycloak

```bash
curl -X POST http://localhost:8080/realms/myrealm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=my-app" \
  -d "client_secret=YOUR_SECRET" \
  -d "username=testuser" \
  -d "password=testpassword123" \
  -d "grant_type=password"
```

Response:
```json
{
  "access_token": "eyJhbGci...",
  "id_token": "eyJhbGci...",
  "expires_in": 900
}
```

**Step 2:** Call protected endpoint in Bruno

```
GET http://localhost:8081/api/protected-resource
Authorization: Bearer eyJhbGci...
```

**Step 3:** Enable Spring Debug and watch console

You should see:
```
DEBUG - Attempting to validate token
DEBUG - Token signature valid
DEBUG - Token not expired
DEBUG - Processing authorization
DEBUG - Access granted
```

### With Frontend

**Step 1:** Login via browser

Visit: `http://localhost:8081/`  
Spring automatically gets tokens (behind the scenes)

**Step 2:** Call debug endpoint

```
GET http://localhost:8081/debug/auth-status
```

Check the response - **does it show authenticated = true?**

**Step 3:** Call protected endpoint

```
GET http://localhost:8081/api/protected-resource
```

Check console for the same debug messages as Bruno

---

## Part 7: Key Questions to Answer

After running the debug tools, answer these:

### Question 1: Is the Frontend Getting Tokens?

```bash
# Run this while logged in via frontend
curl http://localhost:8081/debug/auth-status -b "JSESSIONID=your_session_id"
```

**Expected:**
```json
{
  "authenticated": true,
  "user": { "username": "..." }
}
```

**If authenticated is FALSE** → Frontend never successfully logged in

### Question 2: Does the Session Have the Token?

In the debug response, check:
```json
"session": {
  "attributeNames": ["SPRING_SECURITY_CONTEXT", ...]
}
```

**Should contain**: `SPRING_SECURITY_CONTEXT`

**If missing** → Session not being created/maintained

### Question 3: Are Cookies Being Sent?

In the debug response, check:
```json
"cookies": [
  { "name": "JSESSIONID", ... }
]
```

**Should show**: At least one session cookie

**If empty** → Cookies not being set or sent

### Question 4: Is the Interceptor Logging Anything?

When you visit the protected endpoint, does the console show the `DebugSecurityInterceptor` output?

**If nothing appears** → Interceptor not registered properly

---

## Part 8: Decode the Tokens to See What's Inside

Add this debug endpoint to see token contents:

```java
@GetMapping("/debug/decode-token")
public Map<String, Object> decodeToken(@AuthenticationPrincipal OidcUser user) {
    if (user == null) {
        return Map.of("error", "Not authenticated");
    }
    
    Map<String, Object> result = new LinkedHashMap<>();
    
    // ID Token
    result.put("idToken", Map.of(
        "claims", user.getIdToken().getClaims(),
        "expiresAt", user.getIdToken().getExpiresAt(),
        "tokenValue", user.getIdToken().getTokenValue().substring(0, 50) + "..."
    ));
    
    // All OIDC attributes
    result.put("attributes", user.getAttributes());
    
    return result;
}
```

Visit: `http://localhost:8081/debug/decode-token`

You'll see the actual claims inside the token.

---

## Part 9: Complete Debugging Checklist

```
□ Enable DEBUG logging in application.yml
□ Add DebugSecurityInterceptor to SecurityConfig
□ Add DebugController with /debug/* endpoints
□ Restart Spring Boot
□ Login via frontend
□ Visit /debug/auth-status - is authenticated=true?
□ Check console for authentication logs
□ Visit /api/protected-resource - does it work?
□ Try same endpoint in Bruno - does it work?
□ Compare the debug output from both scenarios
□ Check if tokens are valid and not expired
□ Verify session cookies are being set
```

---

## Part 10: Common Issues You'll Find

### Issue 1: "authenticated=false" in debug endpoint

**Cause**: Frontend login didn't work properly  
**Check**: Did you see successful OAuth2 redirect logs?  
**Fix**: Clear cookies, re-login, watch console for errors

### Issue 2: "JSESSIONID cookie missing"

**Cause**: Session not created  
**Check**: SecurityConfig has sessions enabled  
**Fix**: Add `.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)`

### Issue 3: "SPRING_SECURITY_CONTEXT not in session"

**Cause**: Authentication not being stored in session  
**Check**: OAuth2Login is properly configured  
**Fix**: Verify `defaultSuccessUrl` redirects after login

### Issue 4: Bruno works, Frontend doesn't

**Cause**: Bruno uses JWT header auth, Frontend uses session auth  
**Check**: Are you sending Authorization header in frontend?  
**Fix**: Frontend should use session cookies, not headers

### Issue 5: Token is expired

**Check**: `idToken.expiresAt` in debug output  
**Fix**: Token lifetime might be very short (check Keycloak settings)

---

## Summary

You now have **complete visibility** into:
1. ✅ When tokens are exchanged
2. ✅ What tokens are stored
3. ✅ When authentication is validated
4. ✅ What the security context contains
5. ✅ How sessions are managed
6. ✅ How to compare Bruno vs Frontend

**Run through the steps above, enable the debug tools, and compare the output. This will reveal exactly where the difference is!**

---

End of Debugging Guide
