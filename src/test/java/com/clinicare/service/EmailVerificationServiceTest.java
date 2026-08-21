package com.clinicare.service;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.entity.TokenPurpose;
import com.clinicare.entity.User;
import com.clinicare.entity.VerificationToken;
import com.clinicare.exception.EmailVerificationException;
import com.clinicare.repository.UserRepository;
import com.clinicare.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final long EXPIRATION_MINUTES = 1440;

    @Mock
    private VerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<VerificationToken> tokenCaptor;
    @Captor
    private ArgumentCaptor<String> toCaptor;
    @Captor
    private ArgumentCaptor<String> subjectCaptor;
    @Captor
    private ArgumentCaptor<String> htmlCaptor;

    private EmailVerificationService newService() {
        return new EmailVerificationService(tokenRepository, userRepository, emailService, BASE_URL, EXPIRATION_MINUTES);
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
    void createTokenAndSendEmail_storesOnlyHashAndEmailsLink() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);

        String raw = svc.createTokenAndSendEmail(user);

        // Raw token is URL-safe Base64 (no padding, no + or /).
        assertThat(raw).matches("[A-Za-z0-9_-]+");
        assertThat(raw.length()).isGreaterThan(20);

        verify(tokenRepository).deleteByUser_IdAndUsedAtIsNull(1L);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(emailService).sendHtmlMessage(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        // Only the hash is stored, never the raw token.
        assertThat(saved.getTokenHash()).isEqualTo(sha256(raw));
        assertThat(saved.getTokenHash()).isNotEqualTo(raw);
        assertThat(saved.getUser().getId()).isEqualTo(1L);
        assertThat(saved.getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(toCaptor.getValue()).isEqualTo("patient@example.com");
        assertThat(subjectCaptor.getValue()).contains("CliniCare");
        String html = htmlCaptor.getValue();
        assertThat(html).contains("CliniCare");
        assertThat(html).contains("http://localhost:8080/api/auth/verify-email?token=" + raw);
        assertThat(html).contains("did not create");
        assertThat(html).contains("expire");
    }

    @Test
    void verifyEmail_validToken_marksVerifiedAndInvalidatesToken() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String raw = "valid-token-value";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(token));

        EmailVerificationResponseDTO result = svc.verifyEmail(raw);

        assertThat(result.verified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void verifyEmail_alreadyVerifiedUser_returnsSuccessAndInvalidatesToken() {
        EmailVerificationService svc = newService();
        User user = user(1L, true);
        String raw = "already-done";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(token));

        EmailVerificationResponseDTO result = svc.verifyEmail(raw);

        assertThat(result.verified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_blankToken_throws() {
        EmailVerificationService svc = newService();
        assertThatThrownBy(() -> svc.verifyEmail("  "))
                .isInstanceOf(EmailVerificationException.class);
        verify(tokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void verifyEmail_unknownToken_throws() {
        EmailVerificationService svc = newService();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.verifyEmail("whatever"))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    void verifyEmail_expiredToken_throwsAndDoesNotVerify() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String raw = "expired";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(null);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.verifyEmail(raw))
                .isInstanceOf(EmailVerificationException.class);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void verifyEmail_usedToken_throws() {
        EmailVerificationService svc = newService();
        User user = user(1L, false);
        String raw = "used";
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setUsedAt(LocalDateTime.now().minusMinutes(5));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.verifyEmail(raw))
                .isInstanceOf(EmailVerificationException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }
}
