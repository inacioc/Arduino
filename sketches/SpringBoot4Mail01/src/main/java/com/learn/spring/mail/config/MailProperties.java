package com.learn.spring.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Library-level mail settings, distinct from Spring's own spring.mail.* properties.
 *
 * <pre>
 * app:
 *   mail:
 *     default-from: no-reply@example.com
 *     encoding: UTF-8
 * </pre>
 */
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** Default sender address used when the message does not specify one. */
    private String defaultFrom;

    /** Charset for the MIME message. Defaults to UTF-8. */
    private String encoding = "UTF-8";

    public String getDefaultFrom() { return defaultFrom; }
    public void setDefaultFrom(String defaultFrom) { this.defaultFrom = defaultFrom; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
}
