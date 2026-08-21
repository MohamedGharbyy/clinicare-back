package com.clinicare.service;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.dto.ResendVerificationResponseDTO;
import com.clinicare.entity.TokenPurpose;
import com.clinicare.entity.User;
import com.clinicare.entity.VerificationToken;
import com.clinicare.exception.EmailVerificationException;
import com.clinicare.exception.VerificationResendCooldownException;
import com.clinicare.repository.UserRepository;
import com.clinicare.repository.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and validates single-use email verification CODES and sends the
 * confirmation email through {@link EmailService}.
 *
 * <p>This replaces the previous verification-link flow. Instead of a long random
 * token embedded in a URL, the user receives a 6-digit numeric code.
 *
 * <p>Security posture:
 * <ul>
 *   <li>The code is a 6-digit value drawn from a {@link SecureRandom} CSPRNG.</li>
 *   <li>Only a SHA-256 hash of {@code "<userId>:<code>"} is persisted, so a
 *       database leak cannot reveal usable codes; the raw code exists solely
 *       inside the verification email and is never returned by the API or
 *       written to logs.</li>
 *   <li>Codes expire after {@code clinicare.email-verification.code-expiration-minutes}
 *       and are invalidated (marked used) after a successful verification.</li>
 *   <li>Generating a new code (on registration or resend) discards any
 *       previously outstanding code for the same user so only the latest works.</li>
 *   <li>A resend cooldown ({@code clinicare.email-verification.resend-cooldown-seconds})
 *       throttles how often a new code can be requested.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

    private static final int CODE_DIGITS = 6;

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long expirationMinutes;
    private final long resendCooldownSeconds;

    public EmailVerificationService(VerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailService emailService,
                                    @Value("${clinicare.email-verification.code-expiration-minutes:10}") long expirationMinutes,
                                    @Value("${clinicare.email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.expirationMinutes = expirationMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    /**
     * Generates a fresh verification code for the user, stores only its hash,
     * and emails the code. Any previously outstanding (unused) code for the same
     * user is discarded so only the latest code works.
     *
     * @return the raw 6-digit code (used internally / for tests; never exposed
     *         via API responses or logs)
     */
    @Transactional
    public String createTokenAndSendEmail(User user) {
        String code = generateCode();
        persistCode(user, code);
        user.setVerificationEmailSentAt(LocalDateTime.now());
        userRepository.save(user);

        sendVerificationEmail(user, code);
        return code;
    }

    /**
     * Validates a 6-digit code submitted for the given account, marks the
     * account email as verified, and invalidates the code. Throws
     * {@link EmailVerificationException} for a missing, unknown, expired, or
     * already-used code, or for an unknown account.
     */
    @Transactional
    public EmailVerificationResponseDTO verifyEmail(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new EmailVerificationException(
                    "The verification code is missing or invalid.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EmailVerificationException(
                        "This verification code is invalid. It may have expired or already been used."));

        VerificationToken token = tokenRepository.findByTokenHash(hashCodeFor(user.getId(), code))
                .orElseThrow(() -> new EmailVerificationException(
                        "This verification code is invalid. It may have expired or already been used."));

        if (token.isUsed()) {
            throw new EmailVerificationException(
                    "This verification code has already been used. Your email is already confirmed.");
        }
        if (token.isExpired()) {
            throw new EmailVerificationException(
                    "This verification code has expired. Please request a new code.");
        }

        boolean alreadyVerified = user.isEmailVerified();

        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        if (alreadyVerified) {
            return new EmailVerificationResponseDTO(true,
                    "Your email address was already verified. You can now sign in to CliniCare.");
        }
        return new EmailVerificationResponseDTO(true,
                "Your email address has been verified. You can now sign in to CliniCare.");
    }

    /**
     * Requests a fresh verification code for the given email.
     *
     * <p>To avoid email enumeration the response is intentionally identical for
     * an unknown address and for an already-verified account. For a genuine
     * pending account the cooldown is enforced and a new code is emailed.
     */
    @Transactional
    public ResendVerificationResponseDTO resendCode(String email) {
        if (email == null || email.isBlank()) {
            return new ResendVerificationResponseDTO(false,
                    "If an account exists for this address, a verification code has been sent.", null);
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty() || maybeUser.get().isEmailVerified()) {
            // Do not reveal whether the account exists or is already verified.
            return new ResendVerificationResponseDTO(false,
                    "If an account exists for this address, a verification code has been sent.", null);
        }

        User user = maybeUser.get();
        enforceCooldown(user);

        String code = generateCode();
        persistCode(user, code);
        user.setVerificationEmailSentAt(LocalDateTime.now());
        userRepository.save(user);

        sendVerificationEmail(user, code);
        return new ResendVerificationResponseDTO(true,
                "A new verification code has been sent to your email.", resendCooldownSeconds);
    }

    private void enforceCooldown(User user) {
        LocalDateTime lastSent = user.getVerificationEmailSentAt();
        if (lastSent == null) {
            return;
        }
        LocalDateTime availableAt = lastSent.plusSeconds(resendCooldownSeconds);
        if (availableAt.isAfter(LocalDateTime.now())) {
            long remaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), availableAt);
            throw new VerificationResendCooldownException(Math.max(remaining, 0));
        }
    }

    private void persistCode(User user, String code) {
        tokenRepository.deleteByUser_IdAndUsedAtIsNull(user.getId());

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(hashCodeFor(user.getId(), code));
        token.setPurpose(TokenPurpose.EMAIL_VERIFICATION);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        tokenRepository.save(token);
    }

    private void sendVerificationEmail(User user, String code) {
        String subject = "Your CliniCare verification code";
        emailService.sendHtmlMessage(user.getEmail(), subject, buildEmailHtml(user, code));
    }

    private String buildEmailHtml(User user, String code) {
        String firstName = Optional.ofNullable(user.getFirstName()).orElse("there");
        String expiryText = expirationMinutes >= 60
                ? Math.max(1, expirationMinutes / 60) + " hours"
                : expirationMinutes + " minutes";
        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/></head>
                <body style="margin:0;background:#f4f6f9;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 4px 16px rgba(0,0,0,0.06);">
                        <tr><td style="background:#38B6FF;padding:18px 32px;">
                          <span style="display:inline-block;background:#ffffff;border-radius:12px;padding:6px;line-height:0;">__LOGO__</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a;">Verify your email address</h1>
                          <p style="margin:0 0 16px;line-height:1.5;color:#374151;">
                            Hello __FIRST_NAME__, thank you for creating a CliniCare account. Use the
                            verification code below to confirm that you own this address and to activate
                            your account.
                          </p>
                          <p style="margin:0 0 8px;line-height:1.5;color:#374151;font-size:14px;">
                            Your CliniCare verification code is:
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                            <tr>
                              <td align="center" style="background:#eaf7ff;border:1px solid #b8e6ff;border-radius:12px;padding:18px 28px;">
                                <span style="font-size:34px;font-weight:700;letter-spacing:8px;color:#0f172a;font-family:'Courier New',monospace;">__CODE__</span>
                              </td>
                            </tr>
                          </table>
                          <p style="margin:0 0 24px;line-height:1.5;color:#374151;font-size:14px;">
                            Enter this code within <strong>__EXPIRY__</strong>. Until you confirm, you
                            will not be able to sign in.
                          </p>
                          <p style="margin:24px 0 0;line-height:1.5;color:#6b7280;font-size:13px;">
                            If you did not create a CliniCare account, you can safely ignore this email.
                            No account will be created and this code will expire on its own.
                          </p>
                        </td></tr>
                        <tr><td style="padding:16px 32px;background:#f8fafc;color:#9ca3af;font-size:12px;">
                          &copy; CliniCare &mdash; This is an automated message, please do not reply.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """;
        return template
                .replace("__FIRST_NAME__", firstName)
                .replace("__CODE__", code)
                .replace("__EXPIRY__", expiryText)
                .replace("__LOGO__", logoHtml());
    }

    private String logoHtml() {
        String uri = EmailTemplateAssets.logoDataUri();
        if (uri == null || uri.isEmpty()) {
            return "<span style=\"font-size:22px;font-weight:700;color:#ffffff;\">CliniCare</span>";
        }
        return "<img src=\"" + uri + "\" alt=\"CliniCare\" width=\"40\" height=\"40\" "
                + "style=\"display:block;width:40px;height:40px;object-fit:contain;\" />";
    }

    private String generateCode() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%0" + CODE_DIGITS + "d", value);
    }

    private String hashCodeFor(Long userId, String code) {
        return hashToken(userId + ":" + code);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
