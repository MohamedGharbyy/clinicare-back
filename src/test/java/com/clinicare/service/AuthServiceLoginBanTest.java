package com.clinicare.service;

import com.clinicare.dto.LoginRequestDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.AccountBannedException;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies that a banned account cannot log in while the ban is active, and that
 * once the ban expires the account is reconciled back to ACTIVE and can log in
 * again (verification scenario 13).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginBanTest {

    @Mock private UserRepository userRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailVerificationService emailVerificationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, patientProfileRepository,
                doctorProfileRepository, passwordEncoder, jwtService, emailVerificationService);
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setEmail("banned@example.com");
        u.setFirstName("Jane");
        u.setLastName("Doe");
        u.setRole(Role.PATIENT);
        u.setEmailVerified(true);
        return u;
    }

    @Test
    void bannedUser_cannotLogin() {
        User u = user();
        u.setStatus(AccountStatus.BANNED);
        u.setBanExpiresAt(LocalDateTime.now().plusDays(3));
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(u));

        LoginRequestDTO request = new LoginRequestDTO("banned@example.com", "password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountBannedException.class);
    }

    @Test
    void expiredBan_allowsLoginAgain() {
        User u = user();
        u.setStatus(AccountStatus.BANNED);
        u.setBanExpiresAt(LocalDateTime.now().minusDays(1)); // already expired
        u.setPasswordHash("hash");
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.generateToken(u)).thenReturn("token");

        LoginRequestDTO request = new LoginRequestDTO("banned@example.com", "password");

        assertThatCode(() -> authService.login(request)).doesNotThrowAnyException();
        // The expired ban is lifted on login.
        assertThat(u.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(u.getBanExpiresAt()).isNull();
    }
}
