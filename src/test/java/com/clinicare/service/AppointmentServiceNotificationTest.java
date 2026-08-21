package com.clinicare.service;

import com.clinicare.dto.AppointmentRequestDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceNotificationTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private Validator validator;
    @Mock private AppointmentNotificationService notificationService;
    @Mock private EmailService emailService;

    @Captor private ArgumentCaptor<Appointment> appointmentCaptor;

    private static final String PATIENT_EMAIL = "patient@example.com";
    private static final String DOCTOR_EMAIL = "doctor@example.com";

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void setPrincipal(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(email);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private AppointmentService serviceWith(AppointmentNotificationService notif) {
        return new AppointmentService(appointmentRepository, userRepository,
                patientProfileRepository, doctorProfileRepository, validator, notif);
    }

    private AppointmentService service() {
        return serviceWith(notificationService);
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

    private Appointment appointment(long id, AppointmentStatus status, long patientId, long doctorId) {
        User patient = user(patientId, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(doctorId, Role.DOCTOR, DOCTOR_EMAIL);
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatient(patientProfile(patientId, patient));
        a.setDoctor(doctorProfile(doctorId, doctor));
        a.setAppointmentDate(LocalDate.of(2026, 9, 1));
        a.setAppointmentTime(LocalTime.of(14, 30));
        a.setReason("Annual check-up");
        a.setStatus(status);
        return a;
    }

    @Test
    void createAppointment_setsPendingAndNotifiesDoctorOnly() {
        setPrincipal(PATIENT_EMAIL);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        when(userRepository.findByEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(patientProfile(1L, patient)));
        when(doctorProfileRepository.findById(2L)).thenReturn(Optional.of(doctorProfile(2L, doctor)));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppointmentRequestDTO request = new AppointmentRequestDTO(2L,
                LocalDate.now().plusDays(1), LocalTime.NOON, "Annual check-up", null);

        AppointmentResponseDTO result = service().createAppointment(request);

        assertThat(result.status()).isEqualTo(AppointmentStatus.PENDING);
        verify(notificationService).notifyRequested(any(Appointment.class));
        verify(notificationService, times(0)).notifyConfirmed(any());
        verify(notificationService, times(0)).notifyRefused(any());
    }

    @Test
    void acceptAppointment_confirmsAndNotifies() {
        setPrincipal(DOCTOR_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        when(userRepository.findByEmail(DOCTOR_EMAIL)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.findByUser(doctor)).thenReturn(Optional.of(doctorProfile(2L, doctor)));
        Appointment existing = appointment(1L, AppointmentStatus.PENDING, 1L, 2L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppointmentResponseDTO result = service().acceptAppointment(1L);

        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(notificationService).notifyConfirmed(any(Appointment.class));
    }

    @Test
    void rejectAppointment_refusesAndNotifiesPatient() {
        setPrincipal(DOCTOR_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        when(userRepository.findByEmail(DOCTOR_EMAIL)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.findByUser(doctor)).thenReturn(Optional.of(doctorProfile(2L, doctor)));
        Appointment existing = appointment(1L, AppointmentStatus.PENDING, 1L, 2L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppointmentResponseDTO result = service().rejectAppointment(1L);

        assertThat(result.status()).isEqualTo(AppointmentStatus.REJECTED);
        verify(notificationService).notifyRefused(any(Appointment.class));
    }

    @Test
    void cancelAppointment_cancelsAndNotifiesWithReason() {
        setPrincipal(PATIENT_EMAIL);
        User patient = user(1L, Role.PATIENT, PATIENT_EMAIL);
        when(userRepository.findByEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patient));
        when(patientProfileRepository.findByUser(patient)).thenReturn(Optional.of(patientProfile(1L, patient)));
        Appointment existing = appointment(1L, AppointmentStatus.CONFIRMED, 1L, 2L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppointmentResponseDTO result = service().cancelAppointment(1L, "Schedule conflict");

        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(notificationService).notifyCancelled(any(Appointment.class), eq("Schedule conflict"));
    }

    @Test
    void adminCancel_cancelsAndNotifies() {
        // AdminService uses a separate notification path; verify it triggers the
        // same cancellation notification without depending on patient/doctor auth.
        AppointmentNotificationService notif = mock(AppointmentNotificationService.class);
        AdminService adminService = new AdminService(patientProfileRepository, doctorProfileRepository,
                appointmentRepository, mock(com.clinicare.repository.PrescriptionRepository.class),
                userRepository, notif, mock(BanAppointmentCancellationService.class));
        Appointment existing = appointment(1L, AppointmentStatus.CONFIRMED, 1L, 2L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppointmentResponseDTO result = adminService.cancelAppointment(1L);

        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(notif).notifyCancelled(any(Appointment.class), eq("Cancelled by the clinic."));
    }

    @Test
    void emailFailure_doesNotRollBackStatusTransition() {
        // Real notification service backed by an email sender that always fails.
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(any(), any(), any());
        AppointmentNotificationService failingNotif =
                new AppointmentNotificationService(emailService, appointmentRepository);
        AppointmentService service = serviceWith(failingNotif);

        setPrincipal(DOCTOR_EMAIL);
        User doctor = user(2L, Role.DOCTOR, DOCTOR_EMAIL);
        when(userRepository.findByEmail(DOCTOR_EMAIL)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.findByUser(doctor)).thenReturn(Optional.of(doctorProfile(2L, doctor)));
        Appointment existing = appointment(1L, AppointmentStatus.PENDING, 1L, 2L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // The status transition must succeed even though the email failed.
        AppointmentResponseDTO result = service.acceptAppointment(1L);

        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, times(1)).save(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }
}
