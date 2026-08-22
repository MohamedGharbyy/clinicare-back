package com.clinicare.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link EmailService}. Uses a mocked {@link JavaMailSender} so it
 * runs fully offline and never touches credentials or a real SMTP server while
 * still verifying the from display name, recipients, subject, and body.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM_ADDRESS = "clinicare.app.support@gmail.com";
    private static final String FROM_NAME = "CliniCare Support";

    @Mock
    private JavaMailSender mailSender;

    private EmailService newService() {
        return new EmailService(mailSender, FROM_ADDRESS, FROM_NAME);
    }

    @Test
    void sendSimpleMessageUsesConfiguredFromDisplayNameAndRecipient() {
        EmailService emailService = newService();

        emailService.sendSimpleMessage("patient@example.com", "Welcome", "Hello there");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        assertThat(sent.getFrom()).isEqualTo("CliniCare Support <clinicare.app.support@gmail.com>");
        assertThat(sent.getTo()).containsExactly("patient@example.com");
        assertThat(sent.getSubject()).isEqualTo("Welcome");
        assertThat(sent.getText()).isEqualTo("Hello there");
    }

    @Test
    void sendHtmlMessagePreservesFromDisplayNameAndSendsHtml() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        newService().sendHtmlMessage("doctor@example.com", "Appointment", "<p>Hi</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getFrom()[0].toString()).contains(FROM_NAME).contains(FROM_ADDRESS);
        assertThat(sent.getSubject()).isEqualTo("Appointment");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sent.writeTo(out);
        assertThat(out.toString()).contains("<p>Hi</p>");
        // A body that does not reference the logo carries no image part.
        assertThat(out.toString()).doesNotContain(EmailTemplateAssets.LOGO_FILE_NAME);
    }

    @Test
    void sendHtmlMessageAttachesTheLogoAsAnInlineCidPartWhenReferenced() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        String html = EmailTemplate.titled("Appointment confirmed").message("Hello").render();
        assertThat(html).contains("cid:" + EmailTemplateAssets.LOGO_CONTENT_ID);

        newService().sendHtmlMessage("patient@example.com", "Appointment confirmed", html);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        captor.getValue().writeTo(out);
        String raw = out.toString();

        // The image travels as a related inline part, not inside the HTML body.
        assertThat(raw).contains("Content-ID: <" + EmailTemplateAssets.LOGO_CONTENT_ID + ">");
        assertThat(raw).contains(EmailTemplateAssets.LOGO_CONTENT_TYPE);
        assertThat(raw).contains("multipart/related");
        assertThat(raw).contains("Content-Disposition: inline");
        assertThat(raw).doesNotContain("data:image");

        // The whole message (HTML + logo part) stays a small, unclipped email.
        assertThat(out.size()).isLessThan(102 * 1024);
    }
}
