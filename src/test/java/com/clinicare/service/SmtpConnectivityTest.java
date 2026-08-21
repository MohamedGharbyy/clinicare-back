package com.clinicare.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Optional, real-world SMTP verification.
 *
 * <p>This test is disabled by default because it performs a live connection to
 * Gmail's SMTP server and therefore requires network access plus the
 * {@code MAIL_USERNAME} / {@code MAIL_PASSWORD} environment variables (never
 * hardcoded). Enable it locally to confirm that outbound email actually works:
 *
 * <pre>
 *   ./mvnw test -Dtest=SmtpConnectivityTest
 * </pre>
 *
 * or remove the {@link Disabled} annotation. Credentials are read from the
 * environment by Spring Mail, so nothing secret is committed.
 */
@SpringBootTest
@Disabled("Enable to verify live Gmail SMTP connectivity; requires network and MAIL_USERNAME/MAIL_PASSWORD env vars")
class SmtpConnectivityTest {

    @Autowired
    private JavaMailSender mailSender;

    @Test
    void smtpConnectionSucceeds() throws Exception {
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        impl.testConnection();
    }
}
