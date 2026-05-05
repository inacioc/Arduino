package com.learn.spring.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Library-level mail settings, distinct from Spring's own {@code spring.mail.*} properties.
 *
 * <pre>
 * app:
 *   mail:
 *     default-from: no-reply@example.com
 *     encoding: UTF-8
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** Default sender address used when the message does not specify one. */
    private String defaultFrom;

    /** Charset for the MIME message. Defaults to UTF-8. */
    private String encoding = "UTF-8";
}
