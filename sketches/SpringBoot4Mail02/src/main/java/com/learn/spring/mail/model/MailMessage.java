package com.learn.spring.mail.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public final class MailMessage {

    /** One or more primary recipients. Required. */
    @Singular("to")
    private final List<String> to;

    @Singular("cc")
    private final List<String> cc;

    @Singular("bcc")
    private final List<String> bcc;

    /** Overrides {@code app.mail.default-from} when set. */
    private final String from;
    private final String replyTo;

    /** Required. */
    private final String subject;

    /** Plain-text body. May be combined with {@link #html} for a multipart/alternative message. */
    private final String text;

    /** HTML body. May be combined with {@link #text} as the plain-text fallback. */
    private final String html;

    @Singular
    private final List<Attachment> attachments;

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    // ------------------------------------------------------------------
    // Lombok generates MailMessageBuilder with all accumulator methods.
    // We only define build() to inject validation before construction.
    // ------------------------------------------------------------------

    public static class MailMessageBuilder {

        public MailMessage build() {
            if (to == null || to.isEmpty()) {
                throw new IllegalStateException("At least one recipient (to) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("Subject must not be blank");
            }
            if (text == null && html == null) {
                throw new IllegalStateException("Either text or html body is required");
            }
            return new MailMessage(
                    List.copyOf(to),
                    cc          == null ? List.of() : List.copyOf(cc),
                    bcc         == null ? List.of() : List.copyOf(bcc),
                    from,
                    replyTo,
                    subject,
                    text,
                    html,
                    attachments == null ? List.of() : List.copyOf(attachments)
            );
        }
    }
}
