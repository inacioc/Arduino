package com.learn.spring.mail;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.learn.spring.mail.model.Attachment;
import com.learn.spring.mail.model.MailMessage;
import com.learn.spring.mail.template.TemplateMailService;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestMailApplication.class)
class MailServiceIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("no-reply@example.com", "secret"));

    @Autowired
    MailService mailService;

    @Autowired
    TemplateMailService templateMailService;

    @BeforeEach
    void resetInbox() {
        greenMail.reset();
    }

    // ------------------------------------------------------------------
    // Plain text
    // ------------------------------------------------------------------

    @Test
    void sendPlainText() throws Exception {
        mailService.send(MailMessage.builder()
                .to("alice@example.com")
                .subject("Hello Alice")
                .text("Plain text body")
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Hello Alice");
        assertThat(received.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
        assertThat(GreenMailUtil.getBody(received).trim()).isEqualTo("Plain text body");
    }

    @Test
    void sendToMultipleRecipients() throws Exception {
        mailService.send(MailMessage.builder()
                .to("alice@example.com", "bob@example.com")
                .subject("Group message")
                .text("Hi everyone")
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 2)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(2);
    }

    // ------------------------------------------------------------------
    // HTML
    // ------------------------------------------------------------------

    @Test
    void sendHtmlOnly() throws Exception {
        String html = "<h1>Hello <b>HTML</b></h1>";

        mailService.send(MailMessage.builder()
                .to("alice@example.com")
                .subject("HTML email")
                .html(html)
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("HTML email");
        assertThat(GreenMailUtil.getBody(received)).contains("Hello");
    }

    @Test
    void sendHtmlWithPlainTextFallback() throws Exception {
        mailService.send(MailMessage.builder()
                .to("alice@example.com")
                .subject("Multipart alternative")
                .text("Fallback plain text")
                .html("<p>Rich HTML content</p>")
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        // The message must be multipart/alternative
        assertThat(received.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) received.getContent();
        assertThat(multipart.getContentType()).containsIgnoringCase("alternative");

        String plainPart = multipart.getBodyPart(0).getContent().toString();
        String htmlPart  = multipart.getBodyPart(1).getContent().toString();

        assertThat(plainPart).contains("Fallback plain text");
        assertThat(htmlPart).contains("Rich HTML content");
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    @Test
    void sendWithAttachment() throws Exception {
        byte[] pdf = "fake-pdf-content".getBytes();

        mailService.send(MailMessage.builder()
                .to("alice@example.com")
                .subject("Your invoice")
                .text("Please find the invoice attached.")
                .attachment(Attachment.of("invoice.pdf", pdf, "application/pdf"))
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) received.getContent();

        boolean hasAttachment = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if ("invoice.pdf".equals(part.getFileName())) {
                hasAttachment = true;
                assertThat(part.getContentType()).containsIgnoringCase("application/pdf");
            }
        }
        assertThat(hasAttachment).as("invoice.pdf attachment expected").isTrue();
    }

    @Test
    void sendWithMultipleAttachments() throws Exception {
        mailService.send(MailMessage.builder()
                .to("alice@example.com")
                .subject("Files")
                .text("Two attachments below.")
                .attachment(Attachment.of("doc.txt",  "text content".getBytes(), "text/plain"))
                .attachment(Attachment.of("data.csv", "a,b,c".getBytes(),        "text/csv"))
                .build());

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        MimeMultipart multipart = (MimeMultipart) received.getContent();

        long attachmentCount = 0;
        for (int i = 0; i < multipart.getCount(); i++) {
            if (multipart.getBodyPart(i).getFileName() != null) {
                attachmentCount++;
            }
        }
        assertThat(attachmentCount).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Thymeleaf template
    // ------------------------------------------------------------------

    @Test
    void sendWithThymeleafTemplate() throws Exception {
        templateMailService.sendWithTemplate(
                MailMessage.builder()
                        .to("alice@example.com")
                        .subject("Welcome, John!"),
                "test-welcome",
                Map.of("name", "John", "message", "Thanks for joining!"));

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Welcome, John!");
        String body = GreenMailUtil.getBody(received);
        assertThat(body).contains("John");
        assertThat(body).contains("Thanks for joining!");
    }

    // ------------------------------------------------------------------
    // Async
    // ------------------------------------------------------------------

    @Test
    void sendAsync() throws Exception {
        mailService.sendAsync(MailMessage.builder()
                .to("alice@example.com")
                .subject("Async email")
                .text("Sent asynchronously")
                .build()).get(5, TimeUnit.SECONDS);

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void buildWithoutRecipientThrows() {
        assertThatThrownBy(() ->
                MailMessage.builder().subject("S").text("B").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void buildWithoutBodyThrows() {
        assertThatThrownBy(() ->
                MailMessage.builder().to("a@b.com").subject("S").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body");
    }
}
