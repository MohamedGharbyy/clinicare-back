package com.clinicare.service;

import com.clinicare.dto.LoginResponseDTO;
import com.clinicare.dto.RegisterRequestDTO;
import com.clinicare.dto.RegisterResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.EmailNotVerifiedException;
import com.clinicare.exception.InvalidCredentialsException;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceVerificationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private RegisterRequestDTO patientRequest() {
        return new RegisterRequestDTO(
                "patient@example.com", "password123", "Jane", "Doe",
                Role.PATIENT, null, "555-1234", null, null);
    }

    private User user(long id, Role role, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setEmail("patient@example.com");
        user.setPasswordHash("hashed");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(verified);
        return user;
    }

    @Test
    void register_createsUnverifiedAccountAndSendsVerificationEmail() {
        when(userRepository.existsByEmail("patient@example.com")).thenReturn(false);

        RegisterResponseDTO result = authService.register(patientRequest());

        assertThat(result.emailVerified()).isFalse();
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
        verify(patientProfileRepository).save(any());
        verify(emailVerificationService).createTokenAndSendEmail(any(User.class));
    }

    @Test
    void login_unverifiedAccount_throwsEmailNotVerified() {
        User user = user(1L, Role.PATIENT, false);
        when(userRepository.findByEmail("patient@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new com.clinicare.dto.LoginRequestDTO("patient@example.com", "password123")))
                .isInstanceOf(EmailNotVerifiedException.class);

        // Password must not be checked before the verification gate.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_verifiedPatient_succeeds() {
        User user = user(1L, Role.PATIENT, true);
        when(userRepository.findByEmail("patient@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        LoginResponseDTO result = authService.login(
                new com.clinicare.dto.LoginRequestDTO("patient@example.com", "password123"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void login_adminAccount_isNotBlockedByVerification() {
        User admin = user(2L, Role.ADMIN, true);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(admin)).thenReturn("admin-jwt");

        LoginResponseDTO result = authService.login(
                new com.clinicare.dto.LoginRequestDTO("admin@example.com", "password123"));

        assertThat(result.token()).isEqualTo("admin-jwt");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void login_verifiedAccount_wrongPassword_throwsInvalidCredentials() {
        User user = user(1L, Role.PATIENT, true);
        when(userRepository.findByEmail("patient@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new com.clinicare.dto.LoginRequestDTO("patient@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
