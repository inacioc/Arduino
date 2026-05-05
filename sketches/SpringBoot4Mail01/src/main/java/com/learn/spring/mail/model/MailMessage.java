package com.learn.spring.mail.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MailMessage {

    private final List<String> to;
    private final List<String> cc;
    private final List<String> bcc;
    private final String from;
    private final String replyTo;
    private final String subject;
    private final String text;
    private final String html;
    private final List<Attachment> attachments;

    private MailMessage(Builder builder) {
        this.to          = Collections.unmodifiableList(new ArrayList<>(builder.to));
        this.cc          = Collections.unmodifiableList(new ArrayList<>(builder.cc));
        this.bcc         = Collections.unmodifiableList(new ArrayList<>(builder.bcc));
        this.from        = builder.from;
        this.replyTo     = builder.replyTo;
        this.subject     = builder.subject;
        this.text        = builder.text;
        this.html        = builder.html;
        this.attachments = Collections.unmodifiableList(new ArrayList<>(builder.attachments));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getTo()               { return to; }
    public List<String> getCc()               { return cc; }
    public List<String> getBcc()              { return bcc; }
    public String getFrom()                   { return from; }
    public String getReplyTo()                { return replyTo; }
    public String getSubject()                { return subject; }
    public String getText()                   { return text; }
    public String getHtml()                   { return html; }
    public List<Attachment> getAttachments()  { return attachments; }

    public boolean hasAttachments() { return !attachments.isEmpty(); }

    // -------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------

    public static final class Builder {

        private final List<String>     to          = new ArrayList<>();
        private final List<String>     cc          = new ArrayList<>();
        private final List<String>     bcc         = new ArrayList<>();
        private final List<Attachment> attachments = new ArrayList<>();
        private String from;
        private String replyTo;
        private String subject;
        private String text;
        private String html;

        private Builder() {}

        public Builder to(String... addresses) {
            this.to.addAll(Arrays.asList(addresses));
            return this;
        }

        public Builder cc(String... addresses) {
            this.cc.addAll(Arrays.asList(addresses));
            return this;
        }

        public Builder bcc(String... addresses) {
            this.bcc.addAll(Arrays.asList(addresses));
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /** Plain-text body. Can be combined with {@link #html} for a multipart/alternative email. */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /** HTML body. Can be combined with {@link #text} as a fallback plain-text part. */
        public Builder html(String html) {
            this.html = html;
            return this;
        }

        public Builder attachment(Attachment attachment) {
            this.attachments.add(Objects.requireNonNull(attachment, "attachment must not be null"));
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            Objects.requireNonNull(attachments, "attachments must not be null");
            this.attachments.addAll(attachments);
            return this;
        }

        public MailMessage build() {
            validate();
            return new MailMessage(this);
        }

        private void validate() {
            if (to.isEmpty()) {
                throw new IllegalStateException("At least one recipient (to) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("Subject must not be blank");
            }
            if (text == null && html == null) {
                throw new IllegalStateException("Either text or html body is required");
            }
        }
    }
}
