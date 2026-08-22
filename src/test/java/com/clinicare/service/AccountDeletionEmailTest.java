package com.clinicare.service;

import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.PrescriptionRepository;
import com.clinicare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the account deletion notification: a successful deletion emails the
 * former account holder exactly once, and a rejected deletion emails nobody.
 *
 * <p>The real {@link AccountNotificationService} is wired to a mocked
 * {@link EmailService} so both the trigger and the email content are verified
 * without touching SMTP or credentials.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionEmailTest {

    private static final String PATIENT_EMAIL = "jane.doe@example.com";
    private static final long ADMIN_ID = 99L;

    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentNotificationService appointmentNotificationService;
    @Mock private BanAppointmentCancellationService banCancellationService;
    @Mock private EmailService emailService;

    @Captor private ArgumentCaptor<String> toCaptor;
    @Captor private ArgumentCaptor<String> subjectCaptor;
    @Captor private ArgumentCaptor<String> htmlCaptor;
    @Captor private ArgumentCaptor<User> userCaptor;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(patientProfileRepository, doctorProfileRepository,
                appointmentRepository, prescriptionRepository, userRepository,
                appointmentNotificationService, banCancellationService,
                new AccountNotificationService(emailService));
    }

    private User user(long id, Role role, AccountStatus status, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setStatus(status);
        u.setEmail(email);
        u.setFirstName("Jane");
        u.setLastName("Doe");
        return u;
    }

    // 1. Successful deletion -> deletion email received.
    @Test
    void successfulDeletion_emailsFormerAccountHolderOnce() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));

        adminService.deleteAccount(1L, ADMIN_ID);

        // Existing deletion behaviour is preserved.
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(userCaptor.getValue().getDeletedAt()).isNotNull();
        assertThat(userCaptor.getValue().getDeletedById()).isEqualTo(ADMIN_ID);
        assertThat(userCaptor.getValue().getBanExpiresAt()).isNull();

        // Exactly one email, sent to the address associated with the account.
        verify(emailService, times(1))
                .sendHtmlMessage(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(PATIENT_EMAIL);
        assertThat(subjectCaptor.getValue()).isEqualTo("Your CliniCare account has been deleted");

        String html = htmlCaptor.getValue();
        assertThat(html).contains("Your CliniCare account has been deleted");
        assertThat(html).contains("Hello Jane");
        assertThat(html).contains(PATIENT_EMAIL);
        assertThat(html).contains("can no longer sign in");
    }

    // 2a. Failed deletion (unknown account) -> no email.
    @Test
    void unknownAccount_sendsNoDeletionEmail() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteAccount(404L, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // 2b. Failed deletion (protected admin account) -> no email.
    @Test
    void adminAccount_sendsNoDeletionEmail() {
        User admin = user(2L, Role.ADMIN, AccountStatus.ACTIVE, "admin@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminService.deleteAccount(2L, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // 2c. Failed deletion (own account) -> no email.
    @Test
    void ownAccount_sendsNoDeletionEmail() {
        User self = user(ADMIN_ID, Role.DOCTOR, AccountStatus.ACTIVE, "self@example.com");
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> adminService.deleteAccount(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // 3. Deleting twice must not produce a duplicate deletion email.
    @Test
    void repeatedDeletion_doesNotSendDuplicateEmail() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));

        adminService.deleteAccount(1L, ADMIN_ID);
        // The account is now DELETED; a second attempt is rejected upstream.
        assertThatThrownBy(() -> adminService.deleteAccount(1L, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been deleted");

        verify(emailService, times(1)).sendHtmlMessage(anyString(), anyString(), anyString());
        verify(userRepository, times(1)).save(any());
    }

    // 4. A failing email must never undo or block the deletion.
    @Test
    void emailFailure_doesNotBreakDeletion() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> adminService.deleteAccount(1L, ADMIN_ID)).doesNotThrowAnyException();

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(AccountStatus.DELETED);
    }

    // 5. The deletion email never leaks credentials or internal identifiers.
    @Test
    void deletionEmail_exposesNoCredentials() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        patient.setPasswordHash("$2a$10$notARealHashButMustNeverBeEmailed");
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));

        adminService.deleteAccount(1L, ADMIN_ID);

        verify(emailService).sendHtmlMessage(anyString(), anyString(), htmlCaptor.capture());
        String html = htmlCaptor.getValue();
        assertThat(html).doesNotContain("$2a$10$");
        assertThat(html.toLowerCase()).doesNotContain("password").doesNotContain("secret")
                .doesNotContain("bearer ").doesNotContain("jwt");
    }

    // ---------------------------------------------------------------------------
    // 6. Disable triggers the disabled-account notification.
    // ---------------------------------------------------------------------------
    @Test
    void disableAccount_sendsDisabledNotification() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        adminService.disableAccount(1L, ADMIN_ID);

        verify(emailService, times(1)).sendHtmlMessage(eq(PATIENT_EMAIL),
                org.mockito.ArgumentMatchers.contains("disabled"), anyString());
        assertThat(patient.getStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    // ---------------------------------------------------------------------------
    // 7. Enable triggers the enabled-account notification.
    // ---------------------------------------------------------------------------
    @Test
    void enableAccount_sendsEnabledNotification() {
        User patient = user(1L, Role.PATIENT, AccountStatus.DISABLED, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        adminService.enableAccount(1L, ADMIN_ID);

        verify(emailService, times(1)).sendHtmlMessage(eq(PATIENT_EMAIL),
                org.mockito.ArgumentMatchers.contains("re-enabled"), anyString());
        assertThat(patient.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ---------------------------------------------------------------------------
    // 8. Ban triggers the banned-account notification.
    // ---------------------------------------------------------------------------
    @Test
    void banAccount_sendsBannedNotification() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        adminService.banAccount(1L, 7, ADMIN_ID);

        verify(emailService, times(1)).sendHtmlMessage(eq(PATIENT_EMAIL),
                org.mockito.ArgumentMatchers.contains("banned"), anyString());
        assertThat(patient.getStatus()).isEqualTo(AccountStatus.BANNED);
    }

    // ---------------------------------------------------------------------------
    // 9. Email failure on disable must not block the status change.
    // ---------------------------------------------------------------------------
    @Test
    void disableAccount_emailFailure_doesNotBlockStatusChange() {
        User patient = user(1L, Role.PATIENT, AccountStatus.ACTIVE, PATIENT_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> adminService.disableAccount(1L, ADMIN_ID))
                .doesNotThrowAnyException();

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getStatus() == AccountStatus.DISABLED));
    }
}
