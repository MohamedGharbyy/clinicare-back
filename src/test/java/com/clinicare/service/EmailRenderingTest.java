package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies how the shared email layout is rendered for every email the
 * application sends (verification, appointment, cancellation, ban and account
 * deletion), focusing on the two reported defects:
 *
 * <ul>
 *   <li><strong>Logo rendering</strong> &mdash; the logo must be an inline
 *       {@code cid:} image sized with matching width/height attributes, never a
 *       {@code data:} URI, an Angular {@code /assets/...} path, a filesystem path
 *       or a relative URL, and never styled with CSS that Gmail strips.</li>
 *   <li><strong>Gmail clipping</strong> &mdash; Gmail clips a message and offers
 *       "View entire message" once the HTML part exceeds roughly 102&nbsp;KB. Each
 *       generated document must stay far below that, be valid and balanced, and
 *       contain no duplicated content or oversized inline image data.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmailRenderingTest {

    /** Gmail starts clipping messages above roughly 102 KB of HTML. */
    private static final int GMAIL_CLIP_LIMIT_BYTES = 102 * 1024;
    /** Budget we hold ourselves to: layout plus content is a few KB. */
    private static final int LIGHTWEIGHT_BUDGET_BYTES = 20 * 1024;

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository tokenRepository;

    // ---------------------------------------------------------------------------
    // Fixtures: each helper renders one real email through a throwaway mock.
    // ---------------------------------------------------------------------------

    private User user(long id, String first, String last, String email) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        return u;
    }

    private Appointment appointment() {
        PatientProfile patient = new PatientProfile();
        patient.setId(1L);
        patient.setUser(user(1L, "Jane", "Doe", "patient@example.com"));

        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(2L);
        doctor.setUser(user(2L, "John", "Smith", "doctor@example.com"));
        doctor.setSpecialty("Cardiology");

        Appointment a = new Appointment();
        a.setId(100L);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(LocalDate.of(2026, 9, 1));
        a.setAppointmentTime(LocalTime.of(14, 30));
        a.setReason("Annual check-up");
        a.setStatus(AppointmentStatus.CONFIRMED);
        return a;
    }

    private String firstHtml(EmailService email, int expectedSends) {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email, times(expectedSends)).sendHtmlMessage(anyString(), anyString(), html.capture());
        return html.getAllValues().get(0);
    }

    private String verificationEmail() {
        EmailService email = mock(EmailService.class);
        new EmailVerificationService(tokenRepository, userRepository, email, 10, 60)
                .createTokenAndSendEmail(user(1L, "Jane", "Doe", "patient@example.com"));
        return firstHtml(email, 1);
    }

    private String appointmentConfirmedEmail() {
        EmailService email = mock(EmailService.class);
        new AppointmentNotificationService(email, appointmentRepository).notifyConfirmed(appointment());
        return firstHtml(email, 2); // patient + doctor; the patient copy is checked
    }

    private String appointmentCancelledEmail() {
        EmailService email = mock(EmailService.class);
        new AppointmentNotificationService(email, appointmentRepository)
                .notifyCancelled(appointment(), "Cancelled by the clinic.");
        return firstHtml(email, 2);
    }

    private String banCancellationEmail() {
        EmailService email = mock(EmailService.class);
        new AppointmentNotificationService(email, appointmentRepository)
                .notifyBannedAccountCancellations(user(1L, "Jane", "Doe", "patient@example.com"),
                        List.of(appointment(), appointment()), LocalDateTime.of(2026, 9, 20, 12, 0));
        return firstHtml(email, 1);
    }

    private String accountDeletedEmail() {
        EmailService email = mock(EmailService.class);
        new AccountNotificationService(email).notifyAccountDeleted(
                user(1L, "Jane", "Doe", "patient@example.com"), LocalDateTime.of(2026, 9, 1, 9, 30));
        return firstHtml(email, 1);
    }

    private String accountDisabledEmail() {
        EmailService email = mock(EmailService.class);
        new AccountNotificationService(email).notifyAccountDisabled(
                user(1L, "Jane", "Doe", "patient@example.com"), LocalDateTime.of(2026, 9, 1, 9, 30));
        return firstHtml(email, 1);
    }

    private String accountEnabledEmail() {
        EmailService email = mock(EmailService.class);
        new AccountNotificationService(email).notifyAccountEnabled(
                user(1L, "Jane", "Doe", "patient@example.com"), LocalDateTime.of(2026, 9, 1, 9, 30));
        return firstHtml(email, 1);
    }

    private String accountBannedEmail() {
        EmailService email = mock(EmailService.class);
        new AccountNotificationService(email).notifyAccountBanned(
                user(1L, "Jane", "Doe", "patient@example.com"), LocalDateTime.of(2026, 9, 20, 12, 0));
        return firstHtml(email, 1);
    }

    private List<String> allEmails() {
        return List.of(verificationEmail(), appointmentConfirmedEmail(), appointmentCancelledEmail(),
                banCancellationEmail(), accountDeletedEmail(), accountDisabledEmail(),
                accountEnabledEmail(), accountBannedEmail());
    }

    // ---------------------------------------------------------------------------
    // Logo rendering
    // ---------------------------------------------------------------------------

    @Test
    void verificationEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(verificationEmail());
    }

    @Test
    void appointmentEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(appointmentConfirmedEmail());
    }

    @Test
    void cancellationEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(appointmentCancelledEmail());
    }

    @Test
    void banCancellationEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(banCancellationEmail());
    }

    @Test
    void accountDeletionEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(accountDeletedEmail());
    }

    @Test
    void accountDisabledEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(accountDisabledEmail());
    }

    @Test
    void accountEnabledEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(accountEnabledEmail());
    }

    @Test
    void accountBannedEmail_rendersInlineCidLogo() {
        assertLogoRendersCorrectly(accountBannedEmail());
    }

    private void assertLogoRendersCorrectly(String html) {
        assertThat(EmailTemplateAssets.hasLogo()).isTrue();

        // Referenced as an inline CID part: no data URI, no Angular asset path, no
        // filesystem path, no relative path and no external host.
        assertThat(html).contains("src=\"cid:" + EmailTemplateAssets.LOGO_CONTENT_ID + "\"");
        assertThat(html).doesNotContain("data:image").doesNotContain("base64,");
        assertThat(html).doesNotContain("/assets/").doesNotContain("file:/")
                .doesNotContain("src=\"/").doesNotContain("src=\"./").doesNotContain("src=\"../")
                .doesNotContain("http://").doesNotContain("https://");
        // Alt text keeps the header readable when images are blocked.
        assertThat(html).contains("alt=\"CliniCare\"");

        // Explicit, equal width/height keep the square asset's aspect ratio and stop
        // clients from up-scaling it. object-fit is stripped by Gmail.
        int width = EmailTemplateAssets.logoDisplayWidthPx();
        int height = EmailTemplateAssets.logoDisplayHeightPx();
        assertThat(html).contains("width=\"" + width + "\" height=\"" + height + "\"");
        assertThat(html).contains("width:" + width + "px;height:" + height + "px");
        assertThat(html).doesNotContain("object-fit");
        assertThat(width).isBetween(48, 160);
        assertThat(height).isBetween(48, 160);

        // Header alignment: the logo sits in a centred header cell above the title.
        assertThat(html).contains("align=\"center\"");
        assertThat(html.indexOf("cid:")).isLessThan(html.indexOf("<h1"));
    }

    // ---------------------------------------------------------------------------
    // Gmail clipping / valid, lightweight HTML
    // ---------------------------------------------------------------------------

    @Test
    void everyEmailStaysWellBelowTheGmailClippingLimit() {
        for (String html : allEmails()) {
            int bytes = html.getBytes(StandardCharsets.UTF_8).length;
            assertThat(bytes)
                    .as("email HTML size")
                    .isLessThan(LIGHTWEIGHT_BUDGET_BYTES)
                    .isLessThan(GMAIL_CLIP_LIMIT_BYTES);
        }
    }

    @Test
    void everyEmailIsValidAndGmailFriendly() {
        for (String html : allEmails()) {
            // One well-formed document; content is never duplicated.
            assertThat(count(html, "<!DOCTYPE html>")).isEqualTo(1);
            assertThat(count(html, "<html")).isEqualTo(1);
            assertThat(count(html, "<body")).isEqualTo(1);
            assertThat(count(html, "</body>")).isEqualTo(1);
            assertThat(count(html, "</html>")).isEqualTo(1);
            assertThat(count(html, "<h1")).isEqualTo(1);

            // Balanced, table-based layout with no runaway nesting.
            assertThat(count(html, "<table")).isEqualTo(count(html, "</table>"));
            assertThat(count(html, "<tr")).isEqualTo(count(html, "</tr>"));
            assertThat(count(html, "<td")).isEqualTo(count(html, "</td>"));
            assertThat(count(html, "<th")).isEqualTo(count(html, "</th>"));
            assertThat(count(html, "<p ")).isEqualTo(count(html, "</p>"));
            assertThat(count(html, "<table")).isLessThanOrEqualTo(4);

            // No client-hostile or clipping-prone constructs.
            assertThat(html).doesNotContain("<script").doesNotContain("javascript:")
                    .doesNotContain("<style").doesNotContain("@import").doesNotContain("<link")
                    .doesNotContain("display:none").doesNotContain("<!--");
        }
    }

    @Test
    void everyEmailIsReadableOnDesktopAndMobile() {
        for (String html : allEmails()) {
            // Fluid card capped at 600px + viewport meta = readable on both.
            assertThat(html).contains("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>");
            assertThat(html).contains("max-width:" + EmailTemplate.MAX_WIDTH_PX + "px");
            assertThat(html).contains("width:100%");
            assertThat(html).doesNotContain("min-width:");
            // Body text is never smaller than 12px.
            assertThat(html).doesNotContain("font-size:11px").doesNotContain("font-size:10px")
                    .doesNotContain("font-size:9px");
            // Existing CliniCare branding: primary blue + the support identity.
            assertThat(html).contains(EmailTemplate.PRIMARY_COLOR);
            assertThat(html).contains("CliniCare Support");
        }
    }

    @Test
    void noEmailExposesCredentialsOrInfrastructureDetails() {
        for (String html : allEmails()) {
            String lower = html.toLowerCase();
            assertThat(lower).doesNotContain("password").doesNotContain("secret")
                    .doesNotContain("mail_username").doesNotContain("mail_password")
                    .doesNotContain("jwt").doesNotContain("bearer ")
                    .doesNotContain("smtp").doesNotContain("localhost");
        }
    }

    @Test
    void importantInformationAppearsNearTheTopOfTheEmail() {
        String html = accountDeletedEmail();
        int titleIndex = html.indexOf("Your CliniCare account has been deleted");
        int footerIndex = html.indexOf("automated message");

        assertThat(titleIndex).isGreaterThan(0).isLessThan(1500);
        assertThat(titleIndex).isLessThan(footerIndex);
    }

    // ---------------------------------------------------------------------------
    // The embedded logo asset itself
    // ---------------------------------------------------------------------------

    @Test
    void embeddedLogoIsSmallAndKeepsTheSourceAspectRatio() throws Exception {
        byte[] embedded = EmailTemplateAssets.logoBytes();
        assertThat(embedded).isNotEmpty();

        BufferedImage source = readSourceLogo();
        BufferedImage inline;
        try (InputStream in = new ByteArrayInputStream(embedded)) {
            inline = ImageIO.read(in);
        }
        assertThat(inline).isNotNull();

        // Re-encoded to header size: sharp on HiDPI screens, never oversized.
        assertThat(Math.max(inline.getWidth(), inline.getHeight()))
                .isEqualTo(EmailTemplateAssets.LOGO_EMBEDDED_MAX_PX);
        assertThat(embedded.length).isLessThan(60 * 1024);

        // Aspect ratio preserved by both the bitmap and the rendered size.
        double sourceRatio = (double) source.getWidth() / source.getHeight();
        assertThat((double) inline.getWidth() / inline.getHeight()).isCloseTo(sourceRatio, within(0.02));
        assertThat((double) EmailTemplateAssets.logoDisplayWidthPx()
                / EmailTemplateAssets.logoDisplayHeightPx()).isCloseTo(sourceRatio, within(0.02));
    }

    @Test
    void inliningTheRawLogoAsABase64DataUriWouldHaveClippedEveryEmail() throws Exception {
        // Regression guard for the original defect: the raw brand asset is far too
        // large to be embedded in the HTML itself.
        byte[] raw;
        try (InputStream in = new ClassPathResource("email/clinicare-logo.png").getInputStream()) {
            raw = in.readAllBytes();
        }
        int base64Length = Base64.getEncoder().encodeToString(raw).length();

        assertThat(base64Length).isGreaterThan(GMAIL_CLIP_LIMIT_BYTES);
        assertThat(EmailTemplateAssets.logoBytes().length).isLessThan(raw.length);
    }

    private BufferedImage readSourceLogo() throws Exception {
        try (InputStream in = new ClassPathResource("email/clinicare-logo.png").getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
