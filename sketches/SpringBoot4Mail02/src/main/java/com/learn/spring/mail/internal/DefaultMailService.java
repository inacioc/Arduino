package com.learn.spring.mail.internal;

import com.learn.spring.mail.MailService;
import com.learn.spring.mail.config.MailProperties;
import com.learn.spring.mail.exception.MailException;
import com.learn.spring.mail.model.Attachment;
import com.learn.spring.mail.model.MailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class DefaultMailService implements MailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    @Override
    public void send(MailMessage message) {
        log.debug("Sending email — subject: '{}', to: {}", message.getSubject(), message.getTo());
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = buildHelper(mime, message);
            applyRecipients(helper, message);
            applyBody(helper, message);
            applyAttachments(helper, message);
            mailSender.send(mime);
            log.debug("Email sent successfully to: {}", message.getTo());
        } catch (MessagingException ex) {
            throw new MailException("Failed to send email to " + message.getTo(), ex);
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(MailMessage message) {
        return CompletableFuture.runAsync(() -> send(message));
    }

    // ------------------------------------------------------------------

    private MimeMessageHelper buildHelper(MimeMessage mime, MailMessage message) throws MessagingException {
        // multipart is required when there are attachments or when both
        // text and html bodies are present (multipart/alternative)
        boolean multipart = message.hasAttachments()
                || (message.getText() != null && message.getHtml() != null);
        return new MimeMessageHelper(mime, multipart, properties.getEncoding());
    }

    private void applyRecipients(MimeMessageHelper helper, MailMessage message) throws MessagingException {
        String from = StringUtils.hasText(message.getFrom())
                ? message.getFrom()
                : properties.getDefaultFrom();
        if (StringUtils.hasText(from)) {
            helper.setFrom(from);
        }

        helper.setTo(message.getTo().toArray(String[]::new));

        if (!message.getCc().isEmpty()) {
            helper.setCc(message.getCc().toArray(String[]::new));
        }
        if (!message.getBcc().isEmpty()) {
            helper.setBcc(message.getBcc().toArray(String[]::new));
        }
        if (StringUtils.hasText(message.getReplyTo())) {
            helper.setReplyTo(message.getReplyTo());
        }

        helper.setSubject(message.getSubject());
    }

    private void applyBody(MimeMessageHelper helper, MailMessage message) throws MessagingException {
        if (StringUtils.hasText(message.getText()) && StringUtils.hasText(message.getHtml())) {
            // Both present → multipart/alternative; email clients prefer HTML,
            // plain text is the fallback.
            helper.setText(message.getText(), message.getHtml());
        } else if (StringUtils.hasText(message.getHtml())) {
            helper.setText(message.getHtml(), true);
        } else {
            helper.setText(message.getText(), false);
        }
    }

    private void applyAttachments(MimeMessageHelper helper, MailMessage message) throws MessagingException {
        for (Attachment attachment : message.getAttachments()) {
            helper.addAttachment(attachment.getName(), attachment.getResource(), attachment.getContentType());
        }
    }
}
