package com.coolapp.medibook.notification;

import com.coolapp.medibook.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final ITemplateEngine templates;
    private final AppProperties props;

    /** Returns false (and logs) instead of throwing, so mail outages never break a booking. */
    public boolean send(String to, String subject, EmailContent content, EmailAttachment attachment) {
        try {
            Context ctx = new Context();
            ctx.setVariable("title", content.title());
            ctx.setVariable("greeting", content.greeting());
            ctx.setVariable("lines", content.lines());
            ctx.setVariable("ctaLabel", content.ctaLabel());
            ctx.setVariable("ctaUrl", content.ctaUrl());
            String html = templates.process("email/notification", ctx);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, attachment != null, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (attachment != null) {
                helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.bytes()), attachment.contentType());
            }
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }
}
