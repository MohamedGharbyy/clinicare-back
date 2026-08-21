package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.CancellationReason;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers automatic appointment cancellation and email notification when a
 * patient or doctor is banned (verification scenarios 1-12).
 */
@ExtendWith(MockitoExtension.class)
class BanAppointmentCancellationServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private EmailService emailService;

    @Captor private ArgumentCaptor<Appointment> appointmentCaptor;
    @Captor private ArgumentCaptor<String> toCaptor;
    @Captor private ArgumentCaptor<String> htmlCaptor;

    private BanAppointmentCancellationService service;

    private static final String PATIENT_EMAIL = "patient@example.com";
    private static final String DOCTOR_EMAIL = "doctor@example.com";

    @BeforeEach
    void setUp() {
        AppointmentNotificationService notif =
                new AppointmentNotificationService(emailService, appointmentRepository);
        service = new BanAppointmentCancellationService(
                appointmentRepository, patientProfileRepository, doctorProfileRepository, notif);
    }

    private User user(long id, Role role, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setEmail(email);
        u.setFirstName(role == Role.PATIENT ? "Jane" : "John");
        u.setLastName(role == Role.PATIENT ? "Doe" : "Smith");
        return u;
    }

    private PatientProfile patientProfile(long id, User user) {
        PatientProfile p = new PatientProfile();
        p.setId(id);
        p.setUser(user);
        return p;
    }

    private DoctorProfile doctorProfile(long id, User user) {
        DoctorProfile d = new DoctorProfile();
        d.setId(id);
        d.setUser(user);
        d.setSpecialty("Cardiology");
        return d;
    }

    private Appointment appointment(long id, AppointmentStatus status,
                                    PatientProfile patient, DoctorProfile doctor,
                                    LocalDateTime when) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(when.toLocalDate());
        a.setAppointmentTime(when.toLocalTime());
        a.setReason("Check-up");
        a.setStatus(status);
        return a;
    }

    // ---------------------------------------------------------------------------
    // 1. Patient banned with affected future appointments.
    // ---------------------------------------------------------------------------
    @Test
    void patientBanned_cancelsFutureAppointmentsAndNotifies() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(10L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<Appointment> cancelled = service.cancelForBannedUser(patient, now, banExpires);

        assertThat(cancelled).hasSize(1);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancellationReason()).isEqualTo(CancellationReason.ACCOUNT_BANNED);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        // Patient + the single affected doctor are each emailed exactly once.
        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getAllValues()).containsExactlyInAnyOrder(PATIENT_EMAIL, DOCTOR_EMAIL);
        assertThat(String.join(" ", htmlCaptor.getAllValues()))
                .contains("banned").contains("cancelled");
    }

    // ---------------------------------------------------------------------------
    // 2. Patient banned with appointments outside the ban period.
    // ---------------------------------------------------------------------------
    @Test
    void patientBanned_appointmentsAfterBanExpiryAreNotTouched() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        // Scheduled AFTER the ban lifts -> not within the ban window.
        Appointment a = appointment(10L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(10));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a));

        List<Appointment> cancelled = service.cancelForBannedUser(patient, now, banExpires);

        assertThat(cancelled).isEmpty();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------
    // 3. Doctor banned with affected future appointments.
    // ---------------------------------------------------------------------------
    @Test
    void doctorBanned_cancelsFutureAppointmentsAndNotifiesPatient() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(20L, AppointmentStatus.PENDING, pp, dp, now.plusDays(3));

        when(doctorProfileRepository.findByUser(doctor)).thenReturn(Optional.of(dp));
        when(appointmentRepository.findByDoctor(dp)).thenReturn(List.of(a));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<Appointment> cancelled = service.cancelForBannedUser(doctor, now, banExpires);

        assertThat(cancelled).hasSize(1);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancellationReason()).isEqualTo(CancellationReason.ACCOUNT_BANNED);
        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getAllValues()).containsExactlyInAnyOrder(PATIENT_EMAIL, DOCTOR_EMAIL);
    }

    // ---------------------------------------------------------------------------
    // 4. Doctor banned with appointments outside the ban period.
    // ---------------------------------------------------------------------------
    @Test
    void doctorBanned_appointmentsAfterBanExpiryAreNotTouched() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(20L, AppointmentStatus.PENDING, pp, dp, now.plusDays(12));

        when(doctorProfileRepository.findByUser(doctor)).thenReturn(Optional.of(dp));
        when(appointmentRepository.findByDoctor(dp)).thenReturn(List.of(a));

        List<Appointment> cancelled = service.cancelForBannedUser(doctor, now, banExpires);

        assertThat(cancelled).isEmpty();
        verify(appointmentRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------
    // 5-7. Terminal appointments are never cancelled.
    // ---------------------------------------------------------------------------
    @Test
    void terminalAppointmentsAreNeverCancelled() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);

        Appointment completed = appointment(30L, AppointmentStatus.COMPLETED, pp, dp, now.plusDays(2));
        Appointment cancelled = appointment(31L, AppointmentStatus.CANCELLED, pp, dp, now.plusDays(2));
        Appointment refused = appointment(32L, AppointmentStatus.REJECTED, pp, dp, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp))
                .thenReturn(List.of(completed, cancelled, refused));

        List<Appointment> result = service.cancelForBannedUser(patient, now, banExpires);

        assertThat(result).isEmpty();
        assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(refused.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
        verify(appointmentRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------
    // 8. Multiple affected appointments -> one consolidated email per recipient.
    // ---------------------------------------------------------------------------
    @Test
    void multipleAffectedAppointments_consolidatedIntoSingleEmails() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a1 = appointment(40L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(1));
        Appointment a2 = appointment(41L, AppointmentStatus.PENDING, pp, dp, now.plusDays(4));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a1, a2));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<Appointment> cancelled = service.cancelForBannedUser(patient, now, banExpires);

        assertThat(cancelled).hasSize(2);
        // Patient + doctor => exactly 2 emails, even though 2 appointments.
        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getAllValues()).containsExactlyInAnyOrder(PATIENT_EMAIL, DOCTOR_EMAIL);
        // The patient's single email lists both appointments.
        String patientEmail = htmlCaptor.getAllValues().get(toCaptor.getAllValues().indexOf(PATIENT_EMAIL));
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy");
        assertThat(patientEmail).contains(a1.getAppointmentDate().format(fmt))
                .contains(a2.getAppointmentDate().format(fmt));
    }

    // ---------------------------------------------------------------------------
    // 9. Multiple affected doctors/patients -> grouped, no duplicate emails.
    // ---------------------------------------------------------------------------
    @Test
    void multipleAffectedDoctors_eachReceivesOneEmail() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpires = now.plusDays(7);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor1 = user(2L, Role.DOCTOR, "doctor1@example.com");
        User doctor2 = user(3L, Role.DOCTOR, "doctor2@example.com");
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp1 = doctorProfile(2L, doctor1);
        DoctorProfile dp2 = doctorProfile(3L, doctor2);
        Appointment a1 = appointment(50L, AppointmentStatus.CONFIRMED, pp, dp1, now.plusDays(1));
        Appointment a2 = appointment(51L, AppointmentStatus.CONFIRMED, pp, dp2, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a1, a2));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancelForBannedUser(patient, now, banExpires);

        // Patient + doctor1 + doctor2 => 3 distinct emails, no duplicates.
        verify(emailService, times(3)).sendHtmlMessage(toCaptor.capture(), anyString(), anyString());
        assertThat(toCaptor.getAllValues()).containsExactlyInAnyOrder(
                PATIENT_EMAIL, "doctor1@example.com", "doctor2@example.com");
    }

    // ---------------------------------------------------------------------------
    // 10. Ban expiration does not restore cancelled appointments.
    // ---------------------------------------------------------------------------
    @Test
    void banExpiration_doesNotRestoreCancelledAppointments() {
        LocalDateTime now = LocalDateTime.now();
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(60L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // First ban cancels the appointment.
        service.cancelForBannedUser(patient, now, now.plusDays(7));
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancellationReason()).isEqualTo(CancellationReason.ACCOUNT_BANNED);

        // Simulate the ban expiring and a later (re)ban. The already-cancelled
        // appointment must remain cancelled and must NOT be re-notified.
        LocalDateTime later = now.plusDays(30);
        List<Appointment> secondPass = service.cancelForBannedUser(patient, later, later.plusDays(7));
        assertThat(secondPass).isEmpty();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancellationReason()).isEqualTo(CancellationReason.ACCOUNT_BANNED);
    }

    // ---------------------------------------------------------------------------
    // 11. Email delivery sends the ban explanation.
    // ---------------------------------------------------------------------------
    @Test
    void emailDelivery_includesBanExplanationAndAppointmentDetails() {
        LocalDateTime now = LocalDateTime.now();
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(70L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancelForBannedUser(patient, now, now.plusDays(7));

        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        String combined = String.join(" ", htmlCaptor.getAllValues());
        assertThat(combined).contains("banned during its scheduled appointment period");
        assertThat(combined).contains(a.getAppointmentDate().format(
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")));
    }

    // ---------------------------------------------------------------------------
    // 12. Email failure must not roll back the ban or the cancellations.
    // ---------------------------------------------------------------------------
    @Test
    void emailFailure_doesNotRollBackCancellations() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        LocalDateTime now = LocalDateTime.now();
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        PatientProfile pp = patientProfile(1L, patient);
        DoctorProfile dp = doctorProfile(2L, doctor);
        Appointment a = appointment(80L, AppointmentStatus.CONFIRMED, pp, dp, now.plusDays(2));

        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(pp));
        when(appointmentRepository.findByPatient(pp)).thenReturn(List.of(a));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // The failure is swallowed: no exception escapes, cancellations persist.
        assertThatCode(() -> service.cancelForBannedUser(patient, now, now.plusDays(7)))
                .doesNotThrowAnyException();

        verify(appointmentRepository, times(1)).save(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentCaptor.getValue().getCancellationReason())
                .isEqualTo(CancellationReason.ACCOUNT_BANNED);
    }
}
