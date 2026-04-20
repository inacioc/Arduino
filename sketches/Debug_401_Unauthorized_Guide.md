# Debugging 401 Unauthorized: Thymeleaf Frontend + Spring Security OIDC Backend

## Problem Statement
✅ Works: Backend REST API with Bruno/Postman  
❌ Fails: Same endpoint from Thymeleaf frontend  
Error: `401 Unauthorized`

---

## Root Cause Analysis: The 5 Most Common Issues

### Issue #1: CSRF Token Missing (Most Common for Thymeleaf Forms)

**Symptom:**
- GET requests work fine
- POST/PUT/DELETE requests get 401
- Works in Bruno (Bruno doesn't need CSRF)

**Why It Happens:**
Spring Security has CSRF protection enabled by default. When Thymeleaf submits a form, you must include the CSRF token.

**The Fix:**

#### In Your Thymeleaf Template (HTML)
```html
<form method="POST" action="/api/protected-resource" th:action="@{/api/protected-resource}">
    <!-- CSRF Token - Required for POST/PUT/DELETE -->
    <input type="hidden" 
           name="_csrf" 
           th:value="${_csrf.token}" />
    
    <!-- Your form fields -->
    <input type="text" name="data" placeholder="Enter data"/>
    
    <button type="submit">Submit</button>
</form>
```

**How It Works:**
- `th:value="${_csrf.token}"` → Thymeleaf injects the CSRF token from Spring Security
- Spring Security auto-adds `_csrf` object to model for all views
- When form submits, the token is sent as hidden field
- Spring validates it before processing POST

#### If Not Working, Check Your SecurityConfig:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf()  // ← Make sure CSRF is NOT disabled
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and()
            .oauth2Login()
            .and()
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .anyRequest().authenticated();
        
        return http.build();
    }
}
```

**Do NOT disable CSRF unless you have a reason:**
```java
// ❌ WRONG - Security hole!
.csrf().disable()

// ✅ RIGHT - Keep it enabled
// (don't add .disable() at all)
```

---

### Issue #2: Missing Session/Cookie (Thymeleaf + REST API Calls)

**Symptom:**
- Thymeleaf page loads (authenticated)
- JavaScript calls to backend API fail with 401
- Session works on page loads but not on AJAX/fetch calls

**Why It Happens:**
When you use JavaScript `fetch()` or `XMLHttpRequest` to call APIs, browsers don't automatically send cookies by default.

**The Fix:**

#### A. JavaScript fetch() - Include Credentials

**❌ WRONG:**
```javascript
// Missing credentials!
fetch('/api/protected-resource')
    .then(response => response.json())
    .then(data => console.log(data));
```

**✅ RIGHT:**
```javascript
// Include credentials (session cookies)
fetch('/api/protected-resource', {
    method: 'GET',
    credentials: 'include',  // ← THIS IS KEY
    headers: {
        'Content-Type': 'application/json'
    }
})
.then(response => {
    if (response.status === 401) {
        console.error('Not authenticated!');
        return;
    }
    return response.json();
})
.then(data => console.log(data));
```

#### B. XMLHttpRequest - Set withCredentials

**✅ RIGHT:**
```javascript
const xhr = new XMLHttpRequest();
xhr.open('GET', '/api/protected-resource', true);
xhr.withCredentials = true;  // ← Include cookies
xhr.setRequestHeader('Content-Type', 'application/json');
xhr.onload = function() {
    if (xhr.status === 200) {
        console.log(JSON.parse(xhr.responseText));
    } else if (xhr.status === 401) {
        console.error('Not authenticated!');
    }
};
xhr.send();
```

#### C. jQuery AJAX - Include Credentials

```javascript
$.ajax({
    url: '/api/protected-resource',
    method: 'GET',
    xhrFields: {
        withCredentials: true  // ← Include cookies
    },
    success: function(data) {
        console.log(data);
    },
    error: function() {
        console.error('Unauthorized');
    }
});
```

---

### Issue #3: CSRF Token Missing from AJAX/fetch Requests

**Symptom:**
- GET requests work
- POST/PUT/DELETE from JavaScript fail with 401

**Why It Happens:**
Even with `credentials: 'include'`, POST requests need the CSRF token in the request.

**The Fix:**

#### Method 1: Extract CSRF Token from Cookie

```javascript
function getCsrfToken() {
    // Spring stores CSRF token in a cookie (default)
    return getCookie('XSRF-TOKEN');
}

function getCookie(name) {
    const nameEQ = name + "=";
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        cookie = cookie.trim();
        if (cookie.indexOf(nameEQ) === 0) {
            return cookie.substring(nameEQ.length);
        }
    }
    return null;
}

// Use in fetch request
fetch('/api/protected-resource', {
    method: 'POST',
    credentials: 'include',
    headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': getCsrfToken()  // ← Add CSRF token
    },
    body: JSON.stringify({ data: 'value' })
})
.then(response => response.json());
```

#### Method 2: Extract from Meta Tag (Recommended)

In your Thymeleaf template, add this in the `<head>`:

```html
<head>
    <meta name="_csrf" th:content="${_csrf.token}"/>
    <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
</head>
```

Then in JavaScript:

```javascript
function getToken() {
    return document.querySelector('meta[name="_csrf"]').getAttribute('content');
}

function getHeader() {
    return document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
}

// Use in fetch
fetch('/api/protected-resource', {
    method: 'POST',
    credentials: 'include',
    headers: {
        'Content-Type': 'application/json',
        [getHeader()]: getToken()  // ← Dynamic header name and token
    },
    body: JSON.stringify({ data: 'value' })
})
.then(response => response.json());
```

#### Method 3: Update SecurityConfig for Custom Header Name

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf()
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .and()
        .oauth2Login()
        .and()
        .authorizeRequests()
            .anyRequest().authenticated();
    
    return http.build();
}
```

---

### Issue #4: Different Endpoints for Frontend vs API

**Symptom:**
- Frontend loads from `http://localhost:8081/`
- API calls to `http://localhost:8082/` fail with 401
- Or calling different domain/port

**Why It Happens:**
CORS (Cross-Origin Resource Sharing) blocks requests to different origins. Cookies are origin-specific and don't follow across domains/ports.

**The Fix:**

#### If Backend and Frontend are on SAME server:
```
Frontend: http://localhost:8081/ (served by Spring)
API: http://localhost:8081/api/* (same Spring app)
✅ Works - same origin, cookies included
```

**Configuration needed:** None, should work automatically.

#### If Backend and Frontend are on DIFFERENT servers:

**Frontend:** `http://localhost:3000` (React, Vue, etc.)  
**Backend:** `http://localhost:8081` (Spring Boot)

**Required Backend Configuration:**

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")  // ← Frontend URL
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .allowCredentials(true)  // ← Allow cookies
                    .maxAge(3600);
            }
        };
    }
}
```

**Frontend Configuration (fetch):**

```javascript
fetch('http://localhost:8081/api/protected-resource', {
    method: 'GET',
    credentials: 'include',  // ← CRITICAL for CORS
    headers: {
        'Content-Type': 'application/json'
    }
})
.then(response => response.json());
```

---

### Issue #5: Bearer Token vs Session Cookie

**Symptom:**
- Works in Bruno when you add `Authorization: Bearer <token>`
- Fails from Thymeleaf frontend
- Frontend doesn't know how to pass JWT token

**Why It Happens:**
You might be using JWT tokens instead of session-based authentication. JWT tokens must be explicitly sent in the `Authorization` header.

**The Fix:**

#### A. Get Token from Session (If Using Session Storage)

```javascript
// Thymeleaf template stores token
// <script>
//   const token = '[[${authToken}]]';
// </script>

fetch('/api/protected-resource', {
    method: 'GET',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token  // ← Pass JWT token
    }
})
.then(response => response.json());
```

#### B. Configure Spring to Accept Bearer Tokens

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2Login()
            .and()
            .oauth2ResourceServer()  // ← For Bearer token support
                .jwt()
            .and()
            .and()
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .anyRequest().authenticated();
        
        return http.build();
    }
}
```

#### C. Extract Token and Pass to Frontend

```java
@RestController
public class TokenController {
    
    @GetMapping("/api/token")
    public ResponseEntity<?> getToken(
        @AuthenticationPrincipal OidcUser principal) {
        
        // Extract the access token from authentication
        String accessToken = principal.getIdToken().getTokenValue();
        
        return ResponseEntity.ok(new {
            token: accessToken
        });
    }
}
```

---

## Systematic Debugging Steps

### Step 1: Enable Debug Logging

Add to `application.yml`:

```yaml
logging:
  level:
    root: INFO
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: DEBUG
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: TRACE
```

Then check logs for:
- `Authentication attempt using ...`
- `Failed to authenticate`
- `Authorization failed`
- `CSRF token validation failed`

### Step 2: Check Network Tab in Browser

**Chrome/Firefox Developer Tools:**

1. Open DevTools → Network tab
2. Make the failing request
3. Click on the request, check:

**Request Headers:**
```
GET /api/protected-resource HTTP/1.1
Host: localhost:8081
Cookie: JSESSIONID=abc123def456  ← ✅ Should be present
X-XSRF-TOKEN: xyz789  ← ✅ Should be present for POST
Authorization: Bearer eyJh...  ← If using JWT
```

**Response Headers:**
```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer realm="..."  ← ✅ Tells what auth is needed
Set-Cookie: JSESSIONID=...  ← ✅ Should be present
```

**Missing any of these? That's your problem!**

### Step 3: Check Browser Console

Look for JavaScript errors:

```javascript
// Run this in console to verify
console.log('Session Cookie:', document.cookie);
console.log('CSRF Token:', document.querySelector('meta[name="_csrf"]')?.content);
```

### Step 4: Test Endpoint with Bruno/Postman

**Try this:**

1. Login via browser (http://localhost:8081/)
2. In Bruno/Postman, copy all cookies from your browser
3. Call the endpoint with those cookies
4. If it works, issue is cookie/session handling
5. If it fails, issue is endpoint/authentication logic

---

## Quick Fix Checklist

Based on your setup, check these in order:

### For Thymeleaf Form Submissions (POST/PUT/DELETE):

```html
<!-- ✅ Always include this -->
<form method="POST" th:action="@{/api/resource}">
    <input type="hidden" name="_csrf" th:value="${_csrf.token}"/>
    <!-- form fields -->
    <button type="submit">Submit</button>
</form>
```

### For JavaScript fetch() Calls:

```javascript
// ✅ Include credentials for session cookies
fetch('/api/protected-resource', {
    method: 'POST',
    credentials: 'include',  // ← KEY for session-based auth
    headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
    },
    body: JSON.stringify(data)
})
.then(response => {
    if (response.status === 401) {
        // User not authenticated
        window.location.href = '/';  // Redirect to login
    }
    return response.json();
});
```

### In SecurityConfig:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ✅ Enable CSRF (don't disable it)
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and()
            
            // ✅ Enable OAuth2 login
            .oauth2Login()
                .defaultSuccessUrl("/", true)
            .and()
            
            // ✅ Enable CORS if needed
            .cors()
            .and()
            
            // ✅ Configure authorization
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            .and()
            
            // ✅ Enable logout
            .logout()
                .logoutSuccessUrl("/");
        
        return http.build();
    }
}
```

---

## Real-World Examples

### Example 1: Thymeleaf Form Submission (Simple)

**Template:**
```html
<form method="POST" th:action="@{/api/save-data}">
    <input type="hidden" name="_csrf" th:value="${_csrf.token}"/>
    
    <input type="text" name="username" placeholder="Username"/>
    <input type="email" name="email" placeholder="Email"/>
    
    <button type="submit">Save</button>
</form>
```

**Backend:**
```java
@PostMapping("/api/save-data")
public ResponseEntity<?> saveData(@RequestParam String username,
                                   @RequestParam String email) {
    return ResponseEntity.ok(new { status: "saved" });
}
```

### Example 2: JavaScript Fetch + CSRF Token

**Template (with token in meta tag):**
```html
<head>
    <meta name="_csrf" th:content="${_csrf.token}"/>
    <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
</head>
<body>
    <button id="saveBtn">Save Data</button>
    <script th:src="@{/js/api.js}"></script>
</body>
```

**JavaScript (api.js):**
```javascript
document.getElementById('saveBtn').addEventListener('click', () => {
    const token = document.querySelector('meta[name="_csrf"]').content;
    const header = document.querySelector('meta[name="_csrf_header"]').content;
    
    fetch('/api/save-data', {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            [header]: token
        },
        body: JSON.stringify({
            username: 'john',
            email: 'john@example.com'
        })
    })
    .then(r => r.json())
    .then(data => console.log('Saved:', data))
    .catch(err => console.error('Error:', err));
});
```

**Backend:**
```java
@PostMapping("/api/save-data")
@ResponseBody
public ResponseEntity<?> saveData(@RequestBody Map<String, String> data) {
    return ResponseEntity.ok(new { status: "saved" });
}
```

### Example 3: CORS + Bearer Token (SPA Frontend)

**Backend (separate from frontend):**
```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("*")
                    .allowedHeaders("*")
                    .allowCredentials(true);
            }
        };
    }
}
```

**Frontend (React/Vue):**
```javascript
async function apiCall(endpoint, options = {}) {
    const token = localStorage.getItem('accessToken');  // Get JWT from storage
    
    const response = await fetch(`http://localhost:8081${endpoint}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` }),
            ...options.headers
        }
    });
    
    if (response.status === 401) {
        // Token expired, redirect to login
        window.location.href = '/login';
    }
    
    return response.json();
}

// Usage
apiCall('/api/protected-resource', { method: 'POST', body: JSON.stringify(data) });
```

---

## Specific Scenarios

### Scenario A: "Works in Bruno, fails in Thymeleaf"

**9/10 times it's one of these:**

1. **Missing CSRF token** (if POST/PUT/DELETE)
   - Fix: Add `th:value="${_csrf.token}"` to form
   
2. **Missing credentials in fetch**
   - Fix: Add `credentials: 'include'` to fetch options
   
3. **Different session/cookie**
   - Fix: Make sure you're using same browser session
   
4. **Different user authenticated**
   - Fix: Check logged-in user in both Bruno and browser

**Test it:**
```bash
# 1. Get your session cookie from Keycloak login
# 2. Use it in Bruno
curl -H "Cookie: JSESSIONID=your_session" \
     http://localhost:8081/api/protected-resource

# Should work if user is authenticated
```

### Scenario B: "Works with GET, fails with POST"

**Cause:** Missing CSRF token

**Fix:**
- For forms: Add `<input type="hidden" name="_csrf" th:value="${_csrf.token}"/>`
- For JavaScript: Add CSRF header to request

### Scenario C: "Redirects to Keycloak instead of returning 401"

**Why:** Endpoint is not protected

**Fix in SecurityConfig:**
```java
.authorizeRequests()
    .antMatchers("/api/protected/**").authenticated()  // ← Explicitly require auth
    .anyRequest().permitAll();
```

---

## Summary: Decision Tree

```
401 Unauthorized Error?
│
├─ Is it a GET request?
│  ├─ YES → Check: Is session cookie present?
│  │         If no → User not authenticated, need fresh login
│  │         If yes → Check: Does endpoint require @PreAuthorize?
│  │
│  └─ NO → Is it POST/PUT/DELETE?
│     ├─ YES → Check: Is CSRF token included?
│     │         If no → Add CSRF token header
│     │         If yes → Check: Is CSRF token valid?
│     │
│     └─ Check: Are credentials included in fetch()?
│            Add credentials: 'include'

Is authentication working but endpoint still returns 401?
│
├─ YES → Issue is authorization (roles), not authentication
│         Check: @PreAuthorize("hasRole('...')")
│         Check: User roles in Keycloak
│
└─ NO → Issue is authentication
       Check: Session exists?
       Check: Token not expired?
       Check: User logged in?
```

---

## Tools for Debugging

### 1. Browser DevTools
- Network tab: Check request/response headers
- Console: Check for JavaScript errors
- Storage: Check cookies and local storage

### 2. Spring Boot Actuator (for metrics)
Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3. Wireshark / Fiddler
- Capture all HTTP traffic
- See exact cookies being sent

### 4. JWT.io
Paste your token to see what's inside:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U
                    ↓
              {
                "alg": "HS256",
                "typ": "JWT"
              }
              {
                "sub": "1234567890"
              }
```

---

End of Guide
