# OpenID Connect with Spring Security & Keycloak: A Complete Learning Guide

## Table of Contents
1. [Part 1: Theory & Fundamentals](#part-1-theory--fundamentals)
2. [Part 2: Architecture & Flows](#part-2-architecture--flows)
3. [Part 3: Keycloak Setup](#part-3-keycloak-setup)
4. [Part 4: Spring Security Configuration](#part-4-spring-security-configuration)
5. [Part 5: Practical Implementation](#part-5-practical-implementation)
6. [Part 6: Security Best Practices](#part-6-security-best-practices)

---

# Part 1: Theory & Fundamentals

## 1.1 The Problem We're Solving

### Before SSO (Single Sign-On)
```
User authenticates to:
- Your App 1 → username/password stored in DB
- Your App 2 → username/password stored in DB
- Your App 3 → username/password stored in DB

Problems:
❌ Users must remember multiple passwords
❌ Each app manages its own user database
❌ Password breaches affect only that app
❌ Inconsistent user experience
❌ Difficult to centralize security policies
```

### With SSO (OpenID Connect)
```
User authenticates to:
- Keycloak (Central Identity Provider)
  ↓
- Your App 1 ← trusts Keycloak
- Your App 2 ← trusts Keycloak
- Your App 3 ← trusts Keycloak

Benefits:
✅ Single login for all apps
✅ Centralized user management
✅ Easier to enforce security policies
✅ Better audit trails
✅ Users feel better (one password)
```

## 1.2 Authentication vs Authorization

Let's clarify two concepts you'll hear constantly:

### Authentication (AuthN)
**"Who are you?"**
- The process of verifying identity
- Proves the user is who they claim to be
- Example: Login with username/password or biometric

### Authorization (AuthZ)
**"What can you do?"**
- The process of determining permissions
- Decides what the authenticated user can access
- Example: Admin can delete users, regular user can only read

```
OpenID Connect = handles Authentication
OAuth 2.0 = handles Authorization (but OpenID adds authentication)
```

## 1.3 What is OpenID Connect?

### The Simple Definition
OpenID Connect (OIDC) is a **thin layer on top of OAuth 2.0** that adds **authentication** capabilities.

It standardizes:
1. **How to authenticate users**
2. **How to get user information** (identity claims)
3. **How to verify the authentication** (digital signatures)

### OpenID Connect vs OAuth 2.0

| Aspect | OAuth 2.0 | OpenID Connect |
|--------|-----------|-----------------|
| **Purpose** | Authorization (access to resources) | Authentication + Authorization |
| **User Info** | Access token only | Access token + ID token |
| **ID Token** | ❌ Not included | ✅ Included (JWT) |
| **Standard Claims** | ❌ No standard | ✅ Standard user claims |
| **Use Case** | "Let app A access your Google Drive" | "Login to app A using your Google account" |

## 1.4 Key Concepts in OpenID Connect

### 1. The Roles

```
┌─────────────────────────────────────────┐
│         YOUR INFRASTRUCTURE             │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────┐  ┌──────────────┐ │
│  │ Resource Owner  │  │ Client App   │ │
│  │  (End User)     │  │ (Spring Boot)│ │
│  │                 │  │              │ │
│  │ "I want to      │  │ "I need to   │ │
│  │  access app A"  │  │  verify who  │ │
│  │                 │  │  they are"   │ │
│  └────────┬────────┘  └──────┬───────┘ │
│           │                  │         │
│           └──────────┬───────┘         │
│                      │                 │
│           ┌──────────▼──────────┐      │
│           │   Authorization     │      │
│           │   Server / IdP      │      │
│           │   (Keycloak)        │      │
│           │                     │      │
│           │ - Authenticates user│      │
│           │ - Issues tokens     │      │
│           │ - Provides user info│      │
│           └─────────────────────┘      │
│                                         │
└─────────────────────────────────────────┘
```

**The Three Players:**

1. **Resource Owner (User)**
   - The person logging in
   - Owns their identity information
   - Grants permission to the app

2. **Client App (Your Spring Boot Application)**
   - Needs to verify the user's identity
   - Trusts the IdP to do authentication
   - Never sees the user's password

3. **Authorization Server / Identity Provider (Keycloak)**
   - Authenticates the user (checks password, MFA, etc.)
   - Issues tokens (ID Token, Access Token)
   - Provides standardized user information
   - Maintains the source of truth for identities

### 2. The Tokens

OpenID Connect uses three types of tokens:

#### A. Authorization Code (Temporary)
```
Duration: Seconds
Purpose: Exchange for tokens
Security: Short-lived, single-use
Format: Random string
```
**Used in:** Step 1 of the flow (temporary permission slip)

#### B. ID Token (JWT) - Authentication
```
Duration: Minutes
Purpose: Proves authentication to your app
Contains: User identity claims (name, email, etc.)
Format: JSON Web Token (digitally signed)
Readable by: Your app can decode it

Example claims inside:
{
  "iss": "https://keycloak.example.com/",      // Issuer
  "sub": "user123",                             // Subject (user ID)
  "name": "John Doe",
  "email": "john@example.com",
  "aud": "my-app",                             // Audience (your app)
  "exp": 1234567890,                           // Expiration time
  "iat": 1234567800                            // Issued at
}
```
**Used in:** Proving the user is authenticated

#### C. Access Token (JWT or Opaque) - Authorization
```
Duration: Hours
Purpose: Access protected resources
Contains: Scopes (what user can do)
Format: JWT or Opaque string
Readable by: Only the authorization server

Example scopes:
- openid (required for OIDC)
- profile (get user's profile)
- email (get user's email)
- custom-scope (your own permissions)
```
**Used in:** Accessing APIs and resources

### 3. JWT (JSON Web Token) - Quick Primer

JWTs are used for ID and Access tokens. Structure:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
├─────────────────────────────┬──────────────────────────────┬─────────────────────────┤
│ Header (Algorithm)           │ Payload (Claims/Data)       │ Signature (Verification)│
├─────────────────────────────┴──────────────────────────────┴─────────────────────────┤
{ "alg": "HS256", "typ": "JWT" }  { "sub": "1234567890", ... }  HMACSHA256(header.payload)
```

**Why JWTs are great:**
- ✅ Self-contained (no database lookup needed)
- ✅ Digitally signed (tamper-proof)
- ✅ Stateless (server doesn't need to store them)
- ✅ Can be verified by multiple servers

---

# Part 2: Architecture & Flows

## 2.1 The OpenID Connect Authorization Code Flow

This is the most common and secure flow. Let's visualize it step-by-step:

```
┌──────────┐                                    ┌──────────────┐
│          │                                    │              │
│  User    │                                    │  Keycloak    │
│  (Browser)                                    │  (IdP)       │
│          │                                    │              │
└────┬─────┘                                    └──────┬───────┘
     │                                                  │
     │  1. Clicks "Login with Keycloak"              │
     │     ──────────────────────────────────────────>│
     │                                                  │
     │  2. Keycloak shows login page                  │
     │     <──────────────────────────────────────────│
     │                                                  │
     │  3. User enters username/password              │
     │     ──────────────────────────────────────────>│
     │                                                  │
     │  4. Keycloak validates credentials             │
     │     Creates authorization code                 │
     │     Redirects back to app with code            │
     │     <──────────────────────────────────────────│
     │                                                  │
     │                                                  │
     └──────────┬───────────────────────────────────────┘
                │
         ┌──────▼──────┐
         │ Your Spring │
         │ Boot App    │
         │ (Backend)   │
         └──────┬──────┘
                │
                │  5. Backend receives code
                │  6. Backend calls Keycloak with:
                │     - code
                │     - client_id
                │     - client_secret (kept secret!)
                │     ───────────────────────────────────────────>
                │
                │  7. Keycloak returns:
                │     - ID Token (proves authentication)
                │     - Access Token (permissions)
                │     - Refresh Token (get new access token)
                │     <───────────────────────────────────────────
                │
                │  8. Backend stores tokens (session)
                │  9. User is logged in!
                │
```

### Why This Flow?

**Key Security Principle:** The user's password NEVER goes to your app.

1. ✅ User password only known to Keycloak
2. ✅ Authorization code is single-use and short-lived
3. ✅ Client secret is kept on backend (not exposed to browser)
4. ✅ Tokens are signed and verifiable

## 2.2 What Happens Next (Session Management)

```
STEP 1: User logs in (as above)
        ↓
STEP 2: Backend stores tokens in session
        user_id: "123"
        id_token: "eyJhbGci..."
        access_token: "eyJhbGci..."
        ↓
STEP 3: User makes request to protected resource
        Request: GET /api/profile
        Session Cookie: "JSESSIONID=abc123"
        ↓
STEP 4: Spring Security intercepts request
        Checks: Is there a valid session?
        Checks: Does session have authentication?
        ↓
STEP 5: Grant access or return 401 Unauthorized
```

## 2.3 Token Lifecycle

```
┌──────────────┐
│ Access Token │
│  Created     │
└──────┬───────┘
       │
       │ Valid for: 15 minutes
       │ (you can customize)
       │
       ├─────────────────────────────────────┐
       │                                     │
       ▼ After 15 min                       │
    EXPIRED                                 │
       │                                     │
       │ Use Refresh Token to get new one    │
       │                                     │
       ▼                                     │
   REFRESHED                                │
   (get new access token)                   │
       │                                     │
       │ Valid for: 30 days                 │
       │ (you can customize)                │
       │                                     │
       ├─────────────────────────────────────┘
       │
       ▼
    EXPIRED
    User must login again


Why this two-tier approach?
- Access tokens: Short-lived (minimize damage if compromised)
- Refresh tokens: Longer-lived (avoid constant logins)
- If refresh token expires: User logs in again
```

---

# Part 3: Keycloak Setup

## 3.1 What is Keycloak?

Keycloak is an **open-source Identity and Access Management (IAM)** solution by Red Hat.

**It provides:**
- User registration and login
- Multi-factor authentication (MFA)
- Social login (Google, Facebook, GitHub)
- LDAP/Active Directory integration
- User federation
- Admin console to manage users
- Standards-based (OpenID Connect, OAuth 2.0, SAML)

## 3.2 Why Keycloak for Learning?

```
✅ Open-source (free)
✅ Self-hosted (full control)
✅ Easy to spin up with Docker
✅ Full-featured (not a watered-down demo)
✅ Great documentation
✅ Used in production by many companies
✅ Can run locally on your machine
```

## 3.3 Starting Keycloak with Docker

### Prerequisite
You need Docker installed. If not, install from: https://www.docker.com/products/docker-desktop

### Command

```bash
docker run --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin123 \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

### What This Does
- **--name keycloak**: Container name
- **-p 8080:8080**: Map port 8080 (Keycloak runs on port 8080)
- **KEYCLOAK_ADMIN=admin**: Admin username
- **KEYCLOAK_ADMIN_PASSWORD=admin123**: Admin password
- **start-dev**: Development mode (auto-creates database)

### After Starting
```
✅ Keycloak is running
📍 Access at: http://localhost:8080
```

## 3.4 Setting Up Your First Client in Keycloak

### Step 1: Login to Admin Console
1. Go to: http://localhost:8080
2. Click "Administration Console"
3. Login with:
   - Username: `admin`
   - Password: `admin123`

### Step 2: Create a Realm (Namespace for your app)

**What is a Realm?**
- A realm is like a namespace or tenant
- Each realm has its own users, clients, and policies
- You could have multiple realms for different environments (dev, staging, prod)

**Steps:**
1. In top-left, click "Keycloak" dropdown
2. Click "Create Realm"
3. Name: `myrealm`
4. Click "Create"

### Step 3: Create a Client (Your Spring Boot App)

**What is a Client?**
- A client is an application that uses Keycloak for authentication
- Your Spring Boot app is a "client" from Keycloak's perspective

**Steps:**
1. In left menu, click "Clients"
2. Click "Create client"
3. Configure:
   ```
   Client ID: my-app
   Name: My Spring Boot Application
   ```
4. Click "Next"

### Step 4: Configure Capability

On the "Capability config" page:
```
✅ Standard flow enabled (needed for web apps)
✅ Direct access grants enabled (for testing)
✅ Service accounts roles enabled (for backend-to-backend)
```

### Step 5: Configure Login Settings

On the "Login settings" page:
```
Home URL: http://localhost:8081/
Valid redirect URIs: http://localhost:8081/login/oauth2/code/keycloak
Valid post logout redirect URIs: http://localhost:8081/
Web origins: http://localhost:8081
```

**Why these URLs matter:**
- `Valid redirect URIs`: After user logs in, Keycloak redirects HERE with the code
- `Web origins`: CORS settings (which domains can call Keycloak)

### Step 6: Get the Client Secret

1. Click on "Credentials" tab
2. Copy the "Client Secret" (you'll need this in Spring Security config)

### Step 7: Create Test Users

1. In left menu, click "Users"
2. Click "Add user"
3. Fill in:
   ```
   Username: testuser
   First name: Test
   Last name: User
   Email: test@example.com
   ```
4. Click "Create"
5. Go to "Credentials" tab
6. Set password: `testpassword123` (check "Set Password" checkbox)

**Repeat for a second user:**
```
Username: admin
First name: Admin
Last name: User
Email: admin@example.com
Password: adminpass123
```

---

# Part 4: Spring Security Configuration

## 4.1 High-Level Architecture

```
┌─────────────────────────────────────────────┐
│         Your Spring Boot Application        │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │    Spring Security Layer            │   │
│  ├─────────────────────────────────────┤   │
│  │ - OidcClientInitiatedLogoutSucces...│   │
│  │ - OidcIdTokenDecoderFactory         │   │
│  │ - OidcUserRequest                   │   │
│  │ - SecurityContext                   │   │
│  └────────────────┬────────────────────┘   │
│                   │                         │
│  ┌────────────────▼─────────────────────┐  │
│  │ OAuth 2.0 / OpenID Connect Layer     │  │
│  ├──────────────────────────────────────┤  │
│  │ - Handles token exchange             │  │
│  │ - Manages tokens (ID, Access, ...)   │  │
│  │ - Calls Keycloak                     │  │
│  └────────────────┬─────────────────────┘  │
│                   │                         │
│  ┌────────────────▼─────────────────────┐  │
│  │ Your Business Logic                  │  │
│  │ (@RestController, Services, etc)    │  │
│  └──────────────────────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
         │
         │ (HTTP calls)
         │
         ▼
    ┌─────────────┐
    │  Keycloak   │
    │  (IdP)      │
    └─────────────┘
```

## 4.2 Dependencies (Maven pom.xml)

Spring Security provides built-in support for OpenID Connect. You need:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-client</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>

<!-- Optional: For JWT handling if needed -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

**What each does:**
- `spring-boot-starter-security`: Core Spring Security
- `spring-security-oauth2-client`: OAuth2/OIDC client support
- `spring-security-oauth2-jose`: JWT/JWS support (validates tokens)
- `jjwt-api`: Easier JWT handling (optional, for advanced use)

## 4.3 Application Properties Configuration

Create file: `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: my-app

  security:
    oauth2:
      client:
        registration:
          keycloak:                          # Registration name (arbitrary)
            client-id: my-app                # Must match Keycloak client ID
            client-secret: YOUR_CLIENT_SECRET # From Keycloak credentials
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:8081/login/oauth2/code/keycloak
            scope: openid,profile,email      # What info we request from IdP
            
        provider:
          keycloak:                          # Must match registration name
            issuer-uri: http://localhost:8080/realms/myrealm
            # Everything else (endpoints) is auto-discovered from issuer-uri

server:
  port: 8081

logging:
  level:
    root: INFO
    org.springframework.security: DEBUG  # Enable to see what's happening
```

**Key Concepts:**

| Property | Meaning |
|----------|---------|
| `client-id` | Your app's identifier in Keycloak |
| `client-secret` | Password for your app (keep it secret!) |
| `redirect-uri` | Where user is sent after login |
| `scope` | What user data you're requesting |
| `issuer-uri` | Keycloak server URL + realm |

## 4.4 Security Configuration Class

Create file: `src/main/java/com/example/config/SecurityConfig.java`

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Configure authentication
            .oauth2Login()          // Enable OAuth2/OIDC login
                .defaultSuccessUrl("/", true)   // Redirect to home after login
            .and()
            
            // Configure authorization (who can access what)
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()      // Anyone can access /public/*
                .anyRequest().authenticated()               // Everything else needs login
            .and()
            
            // Enable logout
            .logout()
                .logoutSuccessUrl("/");             // Redirect to home after logout
        
        return http.build();
    }
}
```

**What this does:**

1. **`.oauth2Login()`**: Enables OAuth2/OIDC login
2. **`.defaultSuccessUrl("/", true)`**: After successful login, go to home page
3. **`.authorizeRequests()`**: Define which URLs need authentication
4. **`.antMatchers("/public/**").permitAll()`**: Allow unauthenticated access to /public/*
5. **`.anyRequest().authenticated()`**: Everything else requires authentication
6. **`.logout()`**: Enable logout functionality

---

# Part 5: Practical Implementation

## 5.1 Create a Simple Spring Boot Project Structure

```
my-app/
├── src/main/java/com/example/
│   ├── config/
│   │   └── SecurityConfig.java          (already created above)
│   ├── controller/
│   │   └── HomeController.java          (create this now)
│   └── MyAppApplication.java            (Spring Boot main class)
├── src/main/resources/
│   ├── application.yml                  (already created above)
│   ├── templates/
│   │   ├── home.html                    (create this)
│   │   ├── login.html                   (optional, customize)
│   │   └── logout.html                  (optional)
│   └── static/
│       └── style.css                    (optional)
└── pom.xml                              (Maven config)
```

## 5.2 HomeController - Accessing User Information

Create: `src/main/java/com/example/controller/HomeController.java`

```java
package com.example.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
        // principal contains the authenticated user info from Keycloak
        
        if (principal != null) {
            // Add user info to model to display in template
            model.addAttribute("username", principal.getPreferredUsername());
            model.addAttribute("email", principal.getEmail());
            model.addAttribute("fullName", principal.getFullName());
            model.addAttribute("isAuthenticated", true);
        } else {
            model.addAttribute("isAuthenticated", false);
        }
        
        return "home";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal OidcUser principal, Model model) {
        // Only authenticated users reach here
        model.addAttribute("user", principal);
        model.addAttribute("email", principal.getEmail());
        model.addAttribute("name", principal.getFullName());
        
        return "profile";
    }

    @GetMapping("/public/about")
    public String about() {
        // Anyone can access this (no authentication required)
        return "about";
    }
}
```

**Key Concepts:**

| Concept | Meaning |
|---------|---------|
| `@AuthenticationPrincipal` | Injects the logged-in user |
| `OidcUser` | User object containing identity claims from Keycloak |
| `principal.getPreferredUsername()` | Username from ID token |
| `principal.getEmail()` | Email from ID token |
| `principal.getFullName()` | Full name from ID token |

## 5.3 HTML Templates

### home.html - Home Page with Login/Logout

Create: `src/main/resources/templates/home.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Home - My App</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #ddd;
            padding-bottom: 20px;
        }
        .auth-button {
            padding: 10px 20px;
            background: #007bff;
            color: white;
            text-decoration: none;
            border-radius: 5px;
        }
        .user-info {
            background: #f0f0f0;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>Welcome to My App</h1>
        <div>
            <!-- If user is authenticated -->
            <span th:if="${isAuthenticated}">
                <strong th:text="${username}"></strong>
                <form method="POST" action="/logout" style="display:inline;">
                    <button type="submit" class="auth-button">Logout</button>
                </form>
            </span>
            <!-- If user is NOT authenticated -->
            <span th:unless="${isAuthenticated}">
                <a href="/oauth2/authorization/keycloak" class="auth-button">
                    Login with Keycloak
                </a>
            </span>
        </div>
    </div>

    <h2>Status</h2>
    <p th:if="${isAuthenticated}">
        You are logged in as <strong th:text="${username}"></strong>
    </p>
    <p th:unless="${isAuthenticated}">
        You are not logged in. <a href="/oauth2/authorization/keycloak">Click here to login</a>
    </p>

    <!-- Show user info if authenticated -->
    <div th:if="${isAuthenticated}" class="user-info">
        <h3>Your Information</h3>
        <p><strong>Username:</strong> <span th:text="${username}"></span></p>
        <p><strong>Email:</strong> <span th:text="${email}"></span></p>
        <p><strong>Full Name:</strong> <span th:text="${fullName}"></span></p>
    </div>

    <!-- Navigation -->
    <nav th:if="${isAuthenticated}">
        <ul>
            <li><a href="/profile">View Profile</a></li>
            <li><a href="/public/about">About</a></li>
        </ul>
    </nav>
</body>
</html>
```

### profile.html - Protected Profile Page

Create: `src/main/resources/templates/profile.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Profile</title>
</head>
<body>
    <h1>Your Profile</h1>
    <p>This page is protected - only authenticated users can see it</p>
    
    <div class="profile">
        <h2 th:text="${name}"></h2>
        <p><strong>Email:</strong> <span th:text="${email}"></span></p>
    </div>
    
    <a href="/">Back to Home</a>
</body>
</html>
```

## 5.4 Main Application Class

Create: `src/main/java/com/example/MyAppApplication.java`

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAppApplication.class, args);
    }
}
```

## 5.5 Maven pom.xml (Complete)

Create/Update: `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>My OpenID Connect App</name>
    <description>Learning OpenID Connect with Spring Security and Keycloak</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security + OAuth2/OIDC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-oauth2-client</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-oauth2-jose</artifactId>
        </dependency>

        <!-- Thymeleaf for HTML templates -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

        <!-- Spring Security Thymeleaf integration -->
        <dependency>
            <groupId>org.thymeleaf.extras</groupId>
            <artifactId>thymeleaf-extras-springsecurity6</artifactId>
        </dependency>

        <!-- For development -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Testing (optional) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

## 5.6 Step-by-Step Execution Guide

### Step 1: Start Keycloak

```bash
docker run --name keycloak -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin123 \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

Wait until you see: `Listening on http://0.0.0.0:8080`

### Step 2: Configure Keycloak (Follow Part 3.4 above)

- Create realm `myrealm`
- Create client `my-app`
- Get client secret
- Create test user

### Step 3: Create Your Spring Boot Project

```bash
# Option A: Using Spring Initializr (GUI)
# Visit: https://start.spring.io
# Dependencies: Spring Web, Spring Security, OAuth2 Client, Thymeleaf
# Download and extract

# Option B: Using Maven
mvn archetype:generate \
  -DgroupId=com.example \
  -DartifactId=my-app \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

### Step 4: Copy Code

Copy the code from sections 5.2, 5.3, 5.4 above into your project

### Step 5: Update application.yml

Replace with your actual client secret from Keycloak:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: my-app
            client-secret: <YOUR_ACTUAL_SECRET_HERE>  # ← IMPORTANT!
            redirect-uri: http://localhost:8081/login/oauth2/code/keycloak
            scope: openid,profile,email
        provider:
          keycloak:
            issuer-uri: http://localhost:8080/realms/myrealm
```

### Step 6: Run Your App

```bash
mvn spring-boot:run
```

Or in your IDE:
1. Right-click `MyAppApplication.java`
2. Select "Run" (or "Run As" → "Java Application")

You should see:
```
Tomcat started on port(s): 8081 with context path ''
Started MyAppApplication in X.XXX seconds
```

### Step 7: Test the Flow

1. **Visit your app**: http://localhost:8081/
2. **Click "Login with Keycloak"**
3. **You're redirected to Keycloak's login page**
4. **Enter credentials**:
   - Username: `testuser`
   - Password: `testpassword123`
5. **Keycloak logs you in**
6. **You're redirected back to your app**
7. **You see your username and email!**

---

# Part 6: Security Best Practices

## 6.1 Token Security

### HTTPS (CRITICAL)
```
❌ WRONG: http://example.com
✅ RIGHT: https://example.com

Tokens are sensitive! Always use HTTPS in production.
Without HTTPS, tokens can be intercepted.
```

### Client Secret Management
```
❌ WRONG: 
  - Store in code: client-secret: abc123def456
  - Commit to git
  - Store in properties file

✅ RIGHT:
  - Environment variables: $CLIENT_SECRET
  - AWS Secrets Manager
  - HashiCorp Vault
  - Docker secrets
  - Spring Cloud Config (encrypted)
```

**Example with environment variables:**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-secret: ${OAUTH_CLIENT_SECRET}  # Loads from env var
```

**To set in terminal:**
```bash
export OAUTH_CLIENT_SECRET=your-secret-here
mvn spring-boot:run
```

## 6.2 Token Validation

Spring Security automatically validates:
- ✅ Token signature (hasn't been tampered with)
- ✅ Token expiration (not expired)
- ✅ Token audience (issued for your app)
- ✅ Token issuer (came from your IdP)

You don't need to do anything extra!

## 6.3 CORS Configuration (If Needed)

If your frontend is on a different domain/port:

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")  // Your frontend
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowCredentials(true);
            }
        };
    }
}
```

## 6.4 Logout with Keycloak

Spring Security's default logout signs out from your app only. For complete logout from Keycloak:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .logout()
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID");
        
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return new OidcClientInitiatedLogoutSuccessHandler(
            clientRegistrationRepository(),
            httpSecurity -> httpSecurity.getRequestDispatcher().forward(
                httpSecurity.getRequest(), 
                httpSecurity.getResponse()
            )
        );
    }
}
```

## 6.5 Access Token Refresh

Tokens expire. Automatically handle refresh:

```java
@Configuration
public class OAuth2SecurityConfig {
    
    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        
        return new InMemoryOAuth2AuthorizedClientService(
            clientRegistrationRepository);
    }
    
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        
        OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()  // ← Automatically refreshes tokens
                .build();
        
        DefaultOAuth2AuthorizedClientManager manager = 
            new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService);
        
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }
}
```

## 6.6 Role-Based Access Control (RBAC)

### Adding Roles in Keycloak

1. Go to Clients → my-app → Roles
2. Create roles: `ADMIN`, `USER`, `MANAGER`
3. Assign to users:
   - Go to Users → testuser → Role mapping
   - Assign roles

### Using Roles in Spring Security

```java
@GetMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")  // Only ADMIN users
public String adminDashboard() {
    return "admin-dashboard";
}

@GetMapping("/user/profile")
@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")  // USER or ADMIN
public String userProfile() {
    return "user-profile";
}
```

Enable method security:

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)  // ← Add this
public class SecurityConfig {
    // ...
}
```

### Getting Roles Programmatically

```java
@GetMapping("/current-roles")
public List<String> getCurrentRoles(@AuthenticationPrincipal OidcUser principal) {
    return principal.getAuthorities()
        .stream()
        .map(auth -> auth.getAuthority())
        .collect(Collectors.toList());
}
```

## 6.7 Protecting REST APIs

If your backend serves JSON APIs (not just web pages):

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/protected")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> protected API() {
        return ResponseEntity.ok("This is protected");
    }

    @GetMapping("/public")
    public ResponseEntity<?> publicAPI() {
        return ResponseEntity.ok("This is public");
    }
}
```

Configuration:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/protected/**").authenticated()
                .anyRequest().authenticated()
            .and()
            .oauth2Login()
            .and()
            .oauth2ResourceServer()  // ← For API protection
                .jwt();  // ← Validate JWTs in Authorization header
        
        return http.build();
    }
}
```

---

## Summary: What You've Learned

✅ **Authentication vs Authorization**
- Authentication proves who you are
- Authorization proves what you can do

✅ **OpenID Connect Fundamentals**
- Built on OAuth 2.0
- Adds authentication layer
- Uses JWT tokens

✅ **The Authorization Code Flow**
- Most secure flow for web apps
- User password never reaches your app
- Tokens are signed and verifiable

✅ **Keycloak Setup**
- Open-source identity provider
- Easy to run locally with Docker
- Full-featured user management

✅ **Spring Security Integration**
- Minimal configuration needed
- Auto-discovery of Keycloak endpoints
- Automatic token validation

✅ **Building Protected Apps**
- Public and protected endpoints
- Access user information from ID token
- Role-based access control

✅ **Security Best Practices**
- HTTPS always (in production)
- Protect client secrets
- Automatic token refresh
- Logout from IdP

---

## Next Steps

1. **Try it yourself**: Follow section 5.6 to get it running
2. **Experiment**: Create new endpoints, add more users, test roles
3. **Explore**: Dive into Keycloak admin console, check the tokens (decode them at jwt.io)
4. **Advanced**: Implement social login (Google, GitHub), LDAP integration, custom attributes

---

## Useful Links

- **Keycloak Docs**: https://www.keycloak.org/documentation.html
- **Spring Security OIDC**: https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html
- **OpenID Connect Spec**: https://openid.net/specs/openid-connect-core-1_0.html
- **JWT Decoder**: https://jwt.io (paste tokens here to see contents)
- **Spring Initializr**: https://start.spring.io

---

## Common Troubleshooting

### "Invalid redirect URI"
**Problem**: Keycloak rejects the redirect after login
**Solution**: Check "Valid redirect URIs" in Keycloak client settings matches your Spring app URL

### "Client secret invalid"
**Problem**: Can't authenticate
**Solution**: Copy the exact secret from Keycloak Credentials tab, paste in application.yml

### "Issuer URI mismatch"
**Problem**: Tokens are rejected
**Solution**: Ensure issuer-uri matches Keycloak realm: `http://localhost:8080/realms/myrealm`

### "CORS error from browser"
**Problem**: Frontend can't call your API
**Solution**: Set "Web origins" in Keycloak client settings to your frontend URL

---

End of Guide
