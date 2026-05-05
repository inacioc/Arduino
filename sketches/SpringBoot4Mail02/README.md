# Spring Boot Mail Library

A lightweight, fluent mail library built on top of **Spring Boot 3** and **Spring Framework**.  
Supports plain text, HTML, multipart/alternative, file attachments, and Thymeleaf templates — all through a single, easy-to-use `MailMessage` builder.

---

## Features

- **Plain text** emails
- **HTML** emails
- **HTML + plain-text fallback** (multipart/alternative)
- **File attachments** (byte array, `Resource`, `InputStream`, `File`)
- **Thymeleaf HTML templates** (activated automatically when Thymeleaf is on the classpath)
- **Asynchronous sending** via `CompletableFuture`
- **Spring Boot auto-configuration** — zero manual bean setup
- Powered by **Lombok** — clean, boilerplate-free model classes

---

## Requirements

| Dependency | Version |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x |
| Lombok | managed by Spring Boot BOM |
| Thymeleaf | optional |

---

## Installation

Add the library to your `pom.xml`:

```xml
<dependency>
    <groupId>com.learn.spring</groupId>
    <artifactId>spring-boot-mail</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Configuration

### `application.yml`

```yaml
# Standard Spring mail / SMTP settings
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: no-reply@example.com
    password: secret
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

# Library-specific settings
app:
  mail:
    default-from: no-reply@example.com   # used when message.from() is not set
    encoding: UTF-8                       # MIME charset (default: UTF-8)
```

### `application.properties` equivalent

```properties
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=no-reply@example.com
spring.mail.password=secret
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

app.mail.default-from=no-reply@example.com
```

---

## Usage

Inject `MailService` (or `TemplateMailService` when Thymeleaf is available) anywhere in your application:

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MailService mailService;
}
```

### Send a plain text email

```java
mailService.send(
    MailMessage.builder()
        .to("alice@example.com")
        .subject("Hello Alice")
        .text("This is a plain text email.")
        .build()
);
```

### Send an HTML email

```java
mailService.send(
    MailMessage.builder()
        .to("alice@example.com")
        .subject("Welcome!")
        .html("<h1>Welcome, Alice!</h1><p>Click <a href='...'>here</a> to get started.</p>")
        .build()
);
```

### Send HTML with a plain-text fallback

Email clients that cannot render HTML will show the plain-text part.

```java
mailService.send(
    MailMessage.builder()
        .to("alice@example.com")
        .subject("Your order confirmation")
        .text("Order #1234 confirmed. Total: $99.00")
        .html("<h2>Order #1234 confirmed</h2><p>Total: <strong>$99.00</strong></p>")
        .build()
);
```

### Send with an attachment

```java
byte[] pdfBytes = generateInvoice();

mailService.send(
    MailMessage.builder()
        .to("alice@example.com")
        .subject("Your invoice")
        .text("Please find your invoice attached.")
        .attachment(Attachment.of("invoice.pdf", pdfBytes, "application/pdf"))
        .build()
);
```

`Attachment` provides four factory overloads:

```java
Attachment.of("report.pdf",  pdfBytes,        "application/pdf")   // byte[]
Attachment.of("report.pdf",  springResource,  "application/pdf")   // Spring Resource
Attachment.of("report.pdf",  inputStream,     "application/pdf")   // InputStream
Attachment.of("report.pdf",  new File("..."))                       // File (content-type auto-detected)
```

### Send to multiple recipients / CC / BCC

`@Singular` generates one-item-at-a-time adder methods — chain them for multiple addresses:

```java
mailService.send(
    MailMessage.builder()
        .to("alice@example.com")
        .to("bob@example.com")
        .cc("manager@example.com")
        .bcc("audit@example.com")
        .replyTo("support@example.com")
        .subject("Team update")
        .text("Here is this week's update.")
        .build()
);
```

Or pass a collection at once:

```java
List<String> recipients = List.of("alice@example.com", "bob@example.com");

MailMessage.builder()
    .to(recipients)   // Collection overload
    .subject("...")
    .text("...")
    .build();
```

### Send with a Thymeleaf template

Add `spring-boot-starter-thymeleaf` to your project. The library auto-configures `TemplateMailService` automatically.

**Template** (`src/main/resources/templates/emails/welcome.html`):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
  <h1>Welcome, <span th:text="${name}">World</span>!</h1>
  <p th:text="${message}">Thank you for joining us.</p>
</body>
</html>
```

**Code**:

```java
@Service
@RequiredArgsConstructor
public class WelcomeService {

    private final TemplateMailService templateMailService;

    public void sendWelcome(String email, String name) {
        templateMailService.sendWithTemplate(
            MailMessage.builder()
                .to(email)
                .subject("Welcome, " + name + "!"),
            "emails/welcome",                          // template name (no .html extension)
            Map.of("name", name, "message", "Thanks for joining us!")
        );
    }
}
```

### Send asynchronously

Both `MailService` and `TemplateMailService` expose async variants returning `CompletableFuture<Void>`:

```java
// Fire and forget
mailService.sendAsync(message);

// Wait for completion or handle errors
mailService.sendAsync(message)
    .exceptionally(ex -> { log.error("Mail failed", ex); return null; });

// Thymeleaf variant
templateMailService.sendWithTemplateAsync(builder, "emails/welcome", vars);
```

---

## Override the sender per message

```java
mailService.send(
    MailMessage.builder()
        .from("orders@example.com")   // overrides app.mail.default-from
        .to("alice@example.com")
        .subject("Your order shipped")
        .text("...")
        .build()
);
```

---

## Architecture

```
MailService (interface)
│
├── DefaultMailService          ← active when Thymeleaf is NOT present
│     JavaMailSender + MimeMessageHelper
│
└── ThymeleafMailService        ← active when Thymeleaf IS present
      extends DefaultMailService
      implements TemplateMailService
      Renders template → HTML → delegates to super.send()

Auto-configuration order:
  TemplateMailAutoConfiguration  (conditional on TemplateEngine.class)
       ↓ runs first
  MailAutoConfiguration          (yields via @ConditionalOnMissingBean)
```

### `MailMessage` builder API (Lombok `@Builder` + `@Singular`)

| Method | Description |
|---|---|
| `.to(String)` / `.to(Collection)` | Add recipient(s) — **required** |
| `.cc(String)` / `.cc(Collection)` | Add CC recipient(s) |
| `.bcc(String)` / `.bcc(Collection)` | Add BCC recipient(s) |
| `.from(String)` | Override default sender |
| `.replyTo(String)` | Set Reply-To address |
| `.subject(String)` | Email subject — **required** |
| `.text(String)` | Plain text body — text or html is **required** |
| `.html(String)` | HTML body |
| `.attachment(Attachment)` | Add a single attachment |
| `.attachments(Collection)` | Add multiple attachments |

---

## Running the tests

Tests use **GreenMail** — an in-memory SMTP server — so no real mail server is needed:

```bash
mvn test
```

The test suite covers:

- Plain text sending
- HTML-only sending
- Multipart/alternative (HTML + plain-text fallback)
- Single and multiple attachments
- Multiple recipients
- Thymeleaf template rendering
- Async sending
- Builder validation (missing recipient, missing body)
