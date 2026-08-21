package com.clinicare.service;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.entity.TokenPurpose;
import com.clinicare.entity.User;
import com.clinicare.entity.VerificationToken;
import com.clinicare.exception.EmailVerificationException;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and validates single-use email verification tokens and sends the
 * confirmation email through {@link EmailService}.
 *
 * <p>Security posture:
 * <ul>
 *   <li>The raw token is a 256-bit value from a {@link SecureRandom} CSPRNG.</li>
 *   <li>Only the SHA-256 hash of the token is persisted, so a database leak
 *       cannot reveal usable tokens; the raw token exists solely inside the
 *       verification link emailed to the user.</li>
 *   <li>Tokens expire (see {@code clinicare.email-verification.token-expiration-minutes})
 *       and are invalidated (marked used) after a successful verification.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 32;

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String appBaseUrl;
    private final long expirationMinutes;

    public EmailVerificationService(VerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailService emailService,
                                    @Value("${clinicare.app.base-url:http://localhost:8080}") String appBaseUrl,
                                    @Value("${clinicare.email-verification.token-expiration-minutes:1440}") long expirationMinutes) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Generates a fresh verification token for the user, stores only its hash,
     * and emails the confirmation link. Any previously outstanding (unused)
     * token for the same user is discarded so only the latest link works.
     *
     * @return the raw token (also embedded in the emailed link)
     */
    @Transactional
    public String createTokenAndSendEmail(User user) {
        tokenRepository.deleteByUser_IdAndUsedAtIsNull(user.getId());

        String rawToken = generateRawToken();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setPurpose(TokenPurpose.EMAIL_VERIFICATION);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        tokenRepository.save(token);

        sendVerificationEmail(user, rawToken);
        return rawToken;
    }

    /**
     * Validates a raw token from the verification link, marks the account email
     * as verified, and invalidates the token. Throws {@link EmailVerificationException}
     * for missing, unknown, expired, or already-used tokens.
     */
    @Transactional
    public EmailVerificationResponseDTO verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new EmailVerificationException("This verification link is missing or invalid.");
        }

        VerificationToken token = tokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new EmailVerificationException(
                        "This verification link is invalid. It may have expired or already been used."));

        if (token.isUsed()) {
            throw new EmailVerificationException(
                    "This verification link has already been used. Your email is already confirmed.");
        }
        if (token.isExpired()) {
            throw new EmailVerificationException(
                    "This verification link has expired. Please request a new confirmation email.");
        }

        User user = token.getUser();
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

    private void sendVerificationEmail(User user, String rawToken) {
        String link = appBaseUrl + "/api/auth/verify-email?token=" + rawToken;
        String subject = "Verify your CliniCare email";
        emailService.sendHtmlMessage(user.getEmail(), subject, buildEmailHtml(user, link));
    }

    private String buildEmailHtml(User user, String link) {
        String firstName = Optional.ofNullable(user.getFirstName()).orElse("there");
        String expiryHours = String.valueOf(Math.max(1, expirationMinutes / 60));
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
                        <tr><td style="background:#0d9488;padding:24px 32px;">
                          <span style="font-size:22px;font-weight:700;color:#ffffff;">CliniCare</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a;">Confirm your email address</h1>
                          <p style="margin:0 0 16px;line-height:1.5;color:#374151;">
                            Hello __FIRST_NAME__, thank you for creating a CliniCare account. We are sending this email
                            to confirm that you own this address and to activate your account.
                          </p>
                          <p style="margin:0 0 24px;line-height:1.5;color:#374151;">
                            Please verify your email within <strong>__EXPIRY_HOURS__ hours</strong> by clicking the button below.
                            Until you confirm, you will not be able to sign in.
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr><td align="center" bgcolor="#0d9488" style="border-radius:8px;">
                              <a href="__LINK__" target="_blank"
                                 style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:600;
                                        color:#ffffff;text-decoration:none;">Verify my email</a>
                            </td></tr>
                          </table>
                          <p style="margin:24px 0 0;line-height:1.5;color:#6b7280;font-size:13px;">
                            If the button does not work, copy and paste this link into your browser:<br/>
                            <a href="__LINK__" style="color:#0d9488;word-break:break-all;">__LINK__</a>
                          </p>
                          <p style="margin:24px 0 0;line-height:1.5;color:#6b7280;font-size:13px;">
                            If you did not create a CliniCare account, you can safely ignore this email.
                            No account will be created and this link will expire on its own.
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
                .replace("__EXPIRY_HOURS__", expiryHours)
                .replace("__LINK__", link);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
