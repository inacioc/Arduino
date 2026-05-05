package com.learn.spring.mail.template;

import com.learn.spring.mail.MailService;
import com.learn.spring.mail.model.MailMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extension of {@link MailService} that renders a Thymeleaf template as the HTML body.
 *
 * <p>Usage:
 * <pre>{@code
 * templateMailService.sendWithTemplate(
 *         MailMessage.builder()
 *                 .to("user@example.com")
 *                 .subject("Welcome!"),
 *         "emails/welcome",          // → src/main/resources/templates/emails/welcome.html
 *         Map.of("name", "John"));
 * }</pre>
 */
public interface TemplateMailService extends MailService {

    /**
     * Renders {@code templateName} with {@code variables}, sets the result as the HTML
     * body on {@code messageBuilder}, then sends the message.
     */
    void sendWithTemplate(MailMessage.Builder messageBuilder, String templateName, Map<String, Object> variables);

    default CompletableFuture<Void> sendWithTemplateAsync(
            MailMessage.Builder messageBuilder, String templateName, Map<String, Object> variables) {
        return CompletableFuture.runAsync(() -> sendWithTemplate(messageBuilder, templateName, variables));
    }
}
