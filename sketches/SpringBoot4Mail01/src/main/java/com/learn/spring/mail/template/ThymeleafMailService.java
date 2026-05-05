package com.learn.spring.mail.template;

import com.learn.spring.mail.config.MailProperties;
import com.learn.spring.mail.internal.DefaultMailService;
import com.learn.spring.mail.model.MailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

public class ThymeleafMailService extends DefaultMailService implements TemplateMailService {

    private final TemplateEngine templateEngine;

    public ThymeleafMailService(JavaMailSender mailSender, MailProperties properties,
                                TemplateEngine templateEngine) {
        super(mailSender, properties);
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendWithTemplate(MailMessage.Builder messageBuilder, String templateName,
                                 Map<String, Object> variables) {
        Context context = new Context(Locale.getDefault(), variables);
        String html = templateEngine.process(templateName, context);
        send(messageBuilder.html(html).build());
    }
}
