package com.learn.spring.mail.template;

import com.learn.spring.mail.MailService;
import com.learn.spring.mail.config.MailProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@ConditionalOnClass({ JavaMailSender.class, TemplateEngine.class })
@EnableConfigurationProperties(MailProperties.class)
public class TemplateMailAutoConfiguration {

    /**
     * Created only when Thymeleaf is on the classpath and no MailService has been
     * registered yet. Implements both TemplateMailService and MailService, so the
     * base MailAutoConfiguration (which runs after this one) will skip its own bean.
     */
    @Bean
    @ConditionalOnMissingBean(MailService.class)
    public TemplateMailService templateMailService(JavaMailSender mailSender,
                                                   MailProperties properties,
                                                   TemplateEngine templateEngine) {
        return new ThymeleafMailService(mailSender, properties, templateEngine);
    }
}
