package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationServiceTest {

    @Mock
    private EmailService emailService;
    @Mock
    private AppointmentRepository appointmentRepository;

    @Captor
    private ArgumentCaptor<String> toCaptor;
    @Captor
    private ArgumentCaptor<String> subjectCaptor;
    @Captor
    private ArgumentCaptor<String> htmlCaptor;

    private AppointmentNotificationService newService() {
        return new AppointmentNotificationService(emailService, appointmentRepository);
    }

    private Appointment appointment(AppointmentStatus status) {
        User patient = user(1L, "Jane", "Doe", "patient@example.com");
        User doctor = user(2L, "John", "Smith", "doctor@example.com");

        PatientProfile patientProfile = new PatientProfile();
        patientProfile.setId(1L);
        patientProfile.setUser(patient);

        DoctorProfile doctorProfile = new DoctorProfile();
        doctorProfile.setId(2L);
        doctorProfile.setUser(doctor);
        doctorProfile.setSpecialty("Cardiology");

        Appointment appointment = new Appointment();
        appointment.setId(100L);
        appointment.setPatient(patientProfile);
        appointment.setDoctor(doctorProfile);
        appointment.setAppointmentDate(LocalDate.of(2026, 9, 1));
        appointment.setAppointmentTime(LocalTime.of(14, 30));
        appointment.setReason("Annual check-up");
        appointment.setStatus(status);
        return appointment;
    }

    private User user(long id, String first, String last, String email) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        return u;
    }

    @Test
    void notifyRequested_emailsDoctorOnlyAndMarksNotified() {
        Appointment a = appointment(AppointmentStatus.PENDING);

        newService().notifyRequested(a);

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("doctor@example.com");

        verify(appointmentRepository, times(1)).save(a);
        assertThat(a.getLastNotifiedStatus()).isEqualTo(AppointmentStatus.PENDING);

        assertThat(subjectCaptor.getValue()).contains("Jane Doe");
        String html = htmlCaptor.getValue();
        assertThat(html).contains("CliniCare");
        assertThat(html).contains("Jane Doe");
        assertThat(html).contains("Dr. Smith");
        assertThat(html).contains("Annual check-up");
        // The internal appointment id (100) is never rendered into the email body.
        assertThat(html).doesNotContain("id=100").doesNotContain(">100<");
    }

    @Test
    void notifyConfirmed_emailsPatientAndDoctor() {
        Appointment a = appointment(AppointmentStatus.CONFIRMED);

        newService().notifyConfirmed(a);

        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());
        List<String> recipients = toCaptor.getAllValues();
        assertThat(recipients).containsExactlyInAnyOrder("patient@example.com", "doctor@example.com");
        assertThat(a.getLastNotifiedStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

        assertThat(subjectCaptor.getAllValues().get(0)).contains("confirmed");
        String combined = String.join(" ", htmlCaptor.getAllValues());
        assertThat(combined).contains("1 September 2026").contains("14:30");
    }

    @Test
    void notifyRefused_emailsPatientOnlyAndStatesRefused() {
        Appointment a = appointment(AppointmentStatus.REJECTED);

        newService().notifyRefused(a);

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), any(), any());
        assertThat(toCaptor.getValue()).isEqualTo("patient@example.com");
        assertThat(a.getLastNotifiedStatus()).isEqualTo(AppointmentStatus.REJECTED);

        verify(emailService).sendHtmlMessage(any(), subjectCaptor.capture(), htmlCaptor.capture());
        assertThat(subjectCaptor.getValue().toLowerCase()).contains("declined");
        String html = htmlCaptor.getValue();
        assertThat(html).contains("declined").contains("Dr. Smith").contains("1 September 2026");
        // The doctor must not be notified about a refusal.
        assertThat(html).doesNotContain("doctor@example.com");
    }

    @Test
    void notifyCancelled_emailsBothPartiesAndIncludesReason() {
        Appointment a = appointment(AppointmentStatus.CANCELLED);

        newService().notifyCancelled(a, "Schedule conflict");

        verify(emailService, times(2)).sendHtmlMessage(toCaptor.capture(), any(), any());
        assertThat(toCaptor.getAllValues())
                .containsExactlyInAnyOrder("patient@example.com", "doctor@example.com");
        assertThat(a.getLastNotifiedStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        verify(emailService, times(2)).sendHtmlMessage(any(), subjectCaptor.capture(), htmlCaptor.capture());
        String combined = String.join(" ", htmlCaptor.getAllValues());
        assertThat(combined).contains("cancelled").contains("Schedule conflict");
    }

    @Test
    void notifyCancelled_withoutReason_omitsReasonRow() {
        Appointment a = appointment(AppointmentStatus.CANCELLED);

        newService().notifyCancelled(a, null);

        verify(emailService, times(2)).sendHtmlMessage(any(), any(), htmlCaptor.capture());
        String combined = String.join(" ", htmlCaptor.getAllValues());
        assertThat(combined).doesNotContain("Cancellation reason");
    }

    @Test
    void duplicateNotifyRequested_doesNotSendTwice() {
        Appointment a = appointment(AppointmentStatus.PENDING);

        AppointmentNotificationService svc = newService();
        svc.notifyRequested(a); // sends + marks notified
        svc.notifyRequested(a); // already notified -> skipped

        verify(emailService, times(1)).sendHtmlMessage(any(), any(), any());
        verify(appointmentRepository, times(1)).save(a);
    }

    @Test
    void emailFailure_doesNotThrowAndDoesNotMarkNotified() {
        Appointment a = appointment(AppointmentStatus.PENDING);
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendHtmlMessage(any(), any(), any());

        assertThatCode(() -> newService().notifyRequested(a)).doesNotThrowAnyException();

        // Failure must not be marked as notified, and must not roll back anything.
        verify(appointmentRepository, never()).save(any());
        assertThat(a.getLastNotifiedStatus()).isNull();
    }
}
