package com.clinicare.service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Reusable service for sending transactional emails through the configured
 * SMTP server (Gmail). Keeps all email construction out of controllers and the
 * Angular frontend so future features (account verification, appointment and
 * ban notifications, and other system messages) can reuse a single entry point.
 *
 * <p>Every message is sent from the support address configured via
 * {@code spring.mail.username}, displayed to recipients as
 * {@code clinicare.mail.from-name} (e.g. "CliniCare Support").
 *
 * <p>HTML bodies are built by {@link EmailTemplate}. When a body references the
 * CliniCare logo through {@code cid:clinicare-logo}, this service attaches the
 * logo as an inline image part, which is the email-compatible way to show it.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username}") String fromAddress,
                        @Value("${clinicare.mail.from-name}") String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    /** Sends a plain-text email. */
    public void sendSimpleMessage(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName + " <" + fromAddress + ">");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * Sends an HTML email. The {@code from} personal name is preserved.
     *
     * <p>When the body references the CliniCare logo through
     * {@code cid:clinicare-logo}, the logo bytes are attached as an inline
     * (CID) image part. Keeping the image out of the HTML is what makes it
     * render in Gmail and keeps the HTML part far below Gmail's clipping limit.
     */
    public void sendHtmlMessage(String to, String subject, String htmlBody) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            attachInlineLogoIfReferenced(helper, htmlBody);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build email message", ex);
        }
        mailSender.send(mimeMessage);
    }

    /** Adds the inline logo part only when the HTML actually references it. */
    private void attachInlineLogoIfReferenced(MimeMessageHelper helper, String htmlBody)
            throws MessagingException {
        if (htmlBody == null
                || !htmlBody.contains("cid:" + EmailTemplateAssets.LOGO_CONTENT_ID)
                || !EmailTemplateAssets.hasLogo()) {
            return;
        }
        ByteArrayResource logo = new ByteArrayResource(EmailTemplateAssets.logoBytes()) {
            @Override
            public String getFilename() {
                return EmailTemplateAssets.LOGO_FILE_NAME;
            }
        };
        helper.addInline(EmailTemplateAssets.LOGO_CONTENT_ID, logo, EmailTemplateAssets.LOGO_CONTENT_TYPE);
    }

    /** Verifies the underlying SMTP connection can be established. */
    public boolean testConnection() {
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            try {
                impl.testConnection();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
        // Fall back to a direct SMTP connect check using the configured session.
        try {
            Session session = Session.getInstance(new Properties());
            try (var transport = session.getTransport("smtp")) {
                transport.connect();
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
    }
}
