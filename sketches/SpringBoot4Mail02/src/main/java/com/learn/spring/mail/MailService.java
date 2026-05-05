package com.learn.spring.mail;

import com.learn.spring.mail.model.MailMessage;

import java.util.concurrent.CompletableFuture;

/**
 * Primary contract for sending emails.
 *
 * <p>Usage:
 * <pre>{@code
 * // Plain text
 * mailService.send(MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("Hello")
 *         .text("Hello World")
 *         .build());
 *
 * // HTML
 * mailService.send(MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("Hello")
 *         .html("<h1>Hello World</h1>")
 *         .build());
 *
 * // HTML + plain-text fallback + attachment
 * mailService.send(MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("Invoice")
 *         .text("See attachment")
 *         .html("<p>See attachment</p>")
 *         .attachment(Attachment.of("invoice.pdf", pdfBytes, "application/pdf"))
 *         .build());
 * }</pre>
 */
public interface MailService {

    void send(MailMessage message);

    CompletableFuture<Void> sendAsync(MailMessage message);
}
