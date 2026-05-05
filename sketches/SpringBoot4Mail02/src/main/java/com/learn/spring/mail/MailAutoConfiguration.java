package com.learn.spring.mail;

import com.learn.spring.mail.config.MailProperties;
import com.learn.spring.mail.internal.DefaultMailService;
import com.learn.spring.mail.template.TemplateMailAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

@AutoConfiguration(after = { MailSenderAutoConfiguration.class, TemplateMailAutoConfiguration.class })
@ConditionalOnClass(JavaMailSender.class)
@EnableConfigurationProperties(MailProperties.class)
public class MailAutoConfiguration {

    /**
     * Fallback bean: created only when no other MailService is present.
     * When Thymeleaf is on the classpath, TemplateMailAutoConfiguration runs first
     * and registers a ThymeleafMailService, so this bean is skipped.
     */
    @Bean
    @ConditionalOnMissingBean(MailService.class)
    public MailService mailService(JavaMailSender mailSender, MailProperties properties) {
        return new DefaultMailService(mailSender, properties);
    }
}
