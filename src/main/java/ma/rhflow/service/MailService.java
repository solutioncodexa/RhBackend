package ma.rhflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.tenant.TenantContext;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final TenantRepository tenantRepository;
    private final boolean enabled;
    private final String from;
    private final String frontendUrl;

    public MailService(
            JavaMailSender mailSender,
            TenantRepository tenantRepository,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.from:rhflow@localhost}") String from,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.mailSender = mailSender;
        this.tenantRepository = tenantRepository;
        this.enabled = enabled;
        this.from = from;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public boolean enabled() {
        return enabled;
    }

    public void send(String to, String subject, String text) {
        send(to, subject, text, null, null);
    }

    public boolean sendPdf(String to, String subject, String text, String filename, byte[] pdf) {
        if (to == null || to.isBlank() || pdf == null) {
            return false;
        }
        if (!enabled) {
            log.info("bulletin non envoyé (SMTP désactivé) to={} subject={}", to, subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text == null ? "" : text, html(companyName(), text, null, null));
            helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
            log.info("bulletin envoyé to={} subject={}", to, subject);
            return true;
        } catch (Exception ex) {
            log.warn("bulletin non délivré (SMTP {}) : {}", to, ex.getMessage());
            return false;
        }
    }

    public void send(String to, String subject, String text, String actionPath, String actionLabel) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (!enabled) {
            log.info("e-mail non envoyé (SMTP désactivé) to={} subject={}", to, subject);
            return;
        }
        String url = actionUrl(actionPath);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text == null ? "" : text, html(companyName(), text, url, actionLabel));
            mailSender.send(message);
            log.info("e-mail envoyé to={} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("e-mail non délivré (SMTP {}) : {}", to, ex.getMessage());
        }
    }

    private String actionUrl(String actionPath) {
        if (actionPath == null || actionPath.isBlank()) {
            return null;
        }
        if (actionPath.startsWith("http://") || actionPath.startsWith("https://")) {
            return actionPath;
        }
        return frontendUrl + (actionPath.startsWith("/") ? actionPath : "/" + actionPath);
    }

    private String companyName() {
        if (tenantRepository == null) {
            return "RHFlow";
        }
        Long id = TenantContext.getTenantId();
        if (id == null) {
            return "RHFlow";
        }
        return tenantRepository.findById(id)
                .map(t -> {
                    if (t.getLegalName() != null && !t.getLegalName().isBlank()) {
                        return t.getLegalName();
                    }
                    return t.getName() == null || t.getName().isBlank() ? "RHFlow" : t.getName();
                })
                .orElse("RHFlow");
    }

    public static String html(String company, String text, String url, String label) {
        StringBuilder body = new StringBuilder();
        body.append("<div style=\"font-family:Arial,sans-serif;color:#1e1b4b;line-height:1.5\">");
        body.append("<p style=\"font-weight:700\">").append(esc(company)).append("</p>");
        for (String line : (text == null ? "" : text).split("\n")) {
            body.append("<p>").append(esc(line)).append("</p>");
        }
        if (url != null && label != null && !label.isBlank()) {
            body.append("<p><a href=\"").append(esc(url))
                    .append("\" style=\"display:inline-block;background:#4f46e5;color:#fff;text-decoration:none;padding:10px 16px;border-radius:8px\">")
                    .append(esc(label)).append("</a></p>");
        }
        body.append("<p style=\"color:#6366f1;font-size:12px\">RHFlow Maroc</p></div>");
        return body.toString();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
