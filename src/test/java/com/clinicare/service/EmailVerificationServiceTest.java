package com.clinicare.service;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.dto.ResendVerificationResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.TokenPurpose;
import com.clinicare.entity.User;
import com.clinicare.entity.VerificationToken;
import com.clinicare.exception.EmailVerificationException;
import com.clinicare.exception.VerificationResendCooldownException;
import com.clinicare.repository.UserRepository;
import com.clinicare.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final long EXPIRATION_MINUTES = 10;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    @Mock
    private VerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<VerificationToken> tokenCaptor;
    @Captor
    private ArgumentCaptor<User> userCaptor;
    @Captor
    private ArgumentCaptor<String> toCaptor;
    @Captor
    private ArgumentCaptor<String> subjectCaptor;
    @Captor
    private ArgumentCaptor<String> htmlCaptor;

    private EmailVerificationService newService() {
        return new EmailVerificationService(tokenRepository, userRepository, emailService,
                EXPIRATION_MINUTES, RESEND_COOLDOWN_SECONDS);
    }

    private User user(long id, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setEmail("patient@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmailVerified(verified);
        return user;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void createTokenAndSendEmail_storesOnlyHashAndEmailsCode() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);

        String code = svc.createTokenAndSendEmail(user);

        // Raw code is exactly 6 numeric digits.
        assertThat(code).matches("\\d{6}");

        verify(tokenRepository).deleteByUser_IdAndUsedAtIsNull(1L);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(userRepository).save(userCaptor.capture());
        verify(emailService).sendHtmlMessage(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        // Only the hash of "<userId>:<code>" is stored, never the raw code.
        assertThat(saved.getTokenHash()).isEqualTo(sha256("1:" + code));
        assertThat(saved.getTokenHash()).isNotEqualTo(code);
        assertThat(saved.getUser().getId()).isEqualTo(1L);
        assertThat(saved.getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userCaptor.getValue().getVerificationEmailSentAt()).isNotNull();

        assertThat(toCaptor.getValue()).isEqualTo("patient@example.com");
        assertThat(subjectCaptor.getValue()).contains("CliniCare");
        String html = htmlCaptor.getValue();
        assertThat(html).contains("CliniCare");
        assertThat(html).contains(code);
        assertThat(html).contains("verification code");
        // No verification link is embedded.
        assertThat(html).doesNotContain("/api/auth/verify-email");
        assertThat(html).doesNotContain("?token=");
    }

    @Test
    void verifyEmail_validCode_marksVerifiedAndInvalidatesCode() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String code = "482731";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(token));

        EmailVerificationResponseDTO result = svc.verifyEmail("patient@example.com", code);

        assertThat(result.verified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void verifyEmail_alreadyVerifiedUser_returnsSuccessAndInvalidatesCode() {
        EmailVerificationService svc = newService();
        User user = user(1L, true);
        String code = "123456";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(token));

        EmailVerificationResponseDTO result = svc.verifyEmail("patient@example.com", code);

        assertThat(result.verified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_unknownAccount_throws() {
        EmailVerificationService svc = newService();
        when(userRepository.findByEmailAndStatusNot("nobody@example.com", AccountStatus.DELETED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.verifyEmail("nobody@example.com", "000000"))
                .isInstanceOf(EmailVerificationException.class);
        verify(tokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void verifyEmail_blankInputs_throw() {
        EmailVerificationService svc = newService();
        assertThatThrownBy(() -> svc.verifyEmail("patient@example.com", "   "))
                .isInstanceOf(EmailVerificationException.class);
        assertThatThrownBy(() -> svc.verifyEmail("  ", "123456"))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    void verifyEmail_unknownCode_throws() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.verifyEmail("patient@example.com", "999999"))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    void verifyEmail_expiredCode_throwsAndDoesNotVerify() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String code = "111111";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.verifyEmail("patient@example.com", code))
                .isInstanceOf(EmailVerificationException.class);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void verifyEmail_usedCode_throws() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String code = "222222";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(LocalDateTime.now().minusMinutes(5));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.verifyEmail("patient@example.com", code))
                .isInstanceOf(EmailVerificationException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_sameCodeCannotBeReused() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String code = "333333";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(token));

        // First use succeeds.
        assertThat(svc.verifyEmail("patient@example.com", code).verified()).isTrue();

        // Second use must fail because the code is now marked used.
        VerificationToken used = new VerificationToken();
        used.setUser(user);
        used.setUsedAt(LocalDateTime.now());
        used.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findByTokenHash(sha256("1:" + code))).thenReturn(Optional.of(used));
        assertThatThrownBy(() -> svc.verifyEmail("patient@example.com", code))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    void resendCode_sendsNewCodeAndInvalidatesPrevious() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        user.setVerificationEmailSentAt(LocalDateTime.now().minusMinutes(5));
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));

        ResendVerificationResponseDTO result = svc.resendCode("patient@example.com");

        assertThat(result.sent()).isTrue();
        verify(tokenRepository).deleteByUser_IdAndUsedAtIsNull(1L);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(emailService).sendHtmlMessage(any(), any(), any());
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(sha256("1:oldcode"));
    }

    @Test
    void resendCode_unknownEmail_doesNotRevealExistence() {
        EmailVerificationService svc = newService();
        when(userRepository.findByEmailAndStatusNot("ghost@example.com", AccountStatus.DELETED)).thenReturn(Optional.empty());

        ResendVerificationResponseDTO result = svc.resendCode("ghost@example.com");

        assertThat(result.sent()).isFalse();
        verify(emailService, never()).sendHtmlMessage(any(), any(), any());
    }

    @Test
    void resendCode_alreadyVerified_doesNotRevealExistence() {
        EmailVerificationService svc = newService();
        User user = user(1L, true);
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));

        ResendVerificationResponseDTO result = svc.resendCode("patient@example.com");

        assertThat(result.sent()).isFalse();
        verify(emailService, never()).sendHtmlMessage(any(), any(), any());
    }

    @Test
    void resendCode_withinCooldown_throws() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        user.setVerificationEmailSentAt(LocalDateTime.now());
        when(userRepository.findByEmailAndStatusNot("patient@example.com", AccountStatus.DELETED)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> svc.resendCode("patient@example.com"))
                .isInstanceOf(VerificationResendCooldownException.class)
                .satisfies(ex -> assertThat(((VerificationResendCooldownException) ex).getRetryAfterSeconds())
                        .isBetween(1L, RESEND_COOLDOWN_SECONDS));
        verify(emailService, never()).sendHtmlMessage(any(), any(), any());
    }
}
