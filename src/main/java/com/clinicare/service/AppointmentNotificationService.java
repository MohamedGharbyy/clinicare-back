package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends transactional appointment emails through {@link EmailService} and keeps
 * the email construction out of {@link AppointmentService}.
 *
 * <p>Each method corresponds to a single lifecycle transition:
 * <ul>
 *   <li>{@link #notifyRequested} — PENDING: tells the doctor a new request waits.</li>
 *   <li>{@link #notifyConfirmed} — CONFIRMED: tells the patient and the doctor.</li>
 *   <li>{@link #notifyRefused} — REJECTED (doctor refused): tells the patient.</li>
 *   <li>{@link #notifyCancelled} — CANCELLED: tells the patient and the doctor.</li>
 * </ul>
 *
 * <p>Reliability guarantees:
 * <ul>
 *   <li>An email failure is caught and logged locally; it never propagates to the
 *       caller, so a failed email can never roll back an appointment status change.</li>
 *   <li>Each status is emailed at most once per appointment. The last successfully
 *       notified status is persisted on the appointment, so re-applying the same
 *       transition (or retrying the operation) will not produce a duplicate email.</li>
 *   <li>No internal database id is placed in the email body; only human-readable
 *       appointment details are shared.</li>
 * </ul>
 *
 * <p>IN_PROGRESS and COMPLETED transitions intentionally do not trigger emails.
 */
@Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> APPOINTMENT_TABLE_HEADERS =
            List.of("Patient", "Doctor", "Date", "Time");

    private final EmailService emailService;
    private final AppointmentRepository appointmentRepository;

    public AppointmentNotificationService(EmailService emailService,
                                          AppointmentRepository appointmentRepository) {
        this.emailService = emailService;
        this.appointmentRepository = appointmentRepository;
    }

    /** PENDING: email the doctor that a new appointment request is waiting. */
    public void notifyRequested(Appointment appointment) {
        if (alreadyNotified(appointment, AppointmentStatus.PENDING)) {
            return;
        }
        String to = appointment.getDoctor().getUser().getEmail();
        try {
            emailService.sendHtmlMessage(to,
                    "New appointment request from " + patientName(appointment),
                    buildRequestedHtml(appointment));
            markNotified(appointment, AppointmentStatus.PENDING);
        } catch (Exception ex) {
            logEmailFailure(appointment, AppointmentStatus.PENDING, to, ex);
        }
    }

    /** CONFIRMED: email both the patient and the doctor for consistency. */
    public void notifyConfirmed(Appointment appointment) {
        if (alreadyNotified(appointment, AppointmentStatus.CONFIRMED)) {
            return;
        }
        String patientEmail = appointment.getPatient().getUser().getEmail();
        String doctorEmail = appointment.getDoctor().getUser().getEmail();
        boolean allSent = true;

        try {
            emailService.sendHtmlMessage(patientEmail,
                    "Your appointment with Dr. " + doctorLastName(appointment) + " is confirmed",
                    buildConfirmedPatientHtml(appointment));
        } catch (Exception ex) {
            allSent = false;
            logEmailFailure(appointment, AppointmentStatus.CONFIRMED, patientEmail, ex);
        }

        try {
            emailService.sendHtmlMessage(doctorEmail,
                    "Appointment with " + patientName(appointment) + " confirmed",
                    buildConfirmedDoctorHtml(appointment));
        } catch (Exception ex) {
            allSent = false;
            logEmailFailure(appointment, AppointmentStatus.CONFIRMED, doctorEmail, ex);
        }

        if (allSent) {
            markNotified(appointment, AppointmentStatus.CONFIRMED);
        }
    }

    /** REJECTED (doctor refused): email the patient only. */
    public void notifyRefused(Appointment appointment) {
        if (alreadyNotified(appointment, AppointmentStatus.REJECTED)) {
            return;
        }
        String to = appointment.getPatient().getUser().getEmail();
        try {
            emailService.sendHtmlMessage(to,
                    "Your appointment request was declined",
                    buildRefusedHtml(appointment));
            markNotified(appointment, AppointmentStatus.REJECTED);
        } catch (Exception ex) {
            logEmailFailure(appointment, AppointmentStatus.REJECTED, to, ex);
        }
    }

    /** CANCELLED: email both the patient and the doctor, including a reason when supplied. */
    public void notifyCancelled(Appointment appointment, String reason) {
        if (alreadyNotified(appointment, AppointmentStatus.CANCELLED)) {
            return;
        }
        String patientEmail = appointment.getPatient().getUser().getEmail();
        String doctorEmail = appointment.getDoctor().getUser().getEmail();
        boolean allSent = true;

        try {
            emailService.sendHtmlMessage(patientEmail,
                    "Your appointment has been cancelled",
                    buildCancelledHtml(appointment, reason, false));
        } catch (Exception ex) {
            allSent = false;
            logEmailFailure(appointment, AppointmentStatus.CANCELLED, patientEmail, ex);
        }

        try {
            emailService.sendHtmlMessage(doctorEmail,
                    "Appointment with " + patientName(appointment) + " cancelled",
                    buildCancelledHtml(appointment, reason, true));
        } catch (Exception ex) {
            allSent = false;
            logEmailFailure(appointment, AppointmentStatus.CANCELLED, doctorEmail, ex);
        }

        if (allSent) {
            markNotified(appointment, AppointmentStatus.CANCELLED);
        }
    }

    /**
     * CANCELLED because of a ban: email the banned account holder once, listing
     * every appointment that was automatically cancelled during the ban period.
     * Works for either a banned patient or a banned doctor &mdash; the recipient
     * is always the banned {@link User}.
     */
    public void notifyBannedAccountCancellations(User bannedUser,
                                                 List<Appointment> appointments,
                                                 LocalDateTime banExpiresAt) {
        if (appointments == null || appointments.isEmpty()) {
            return;
        }
        String to = bannedUser.getEmail();
        try {
            emailService.sendHtmlMessage(to,
                    "Your CliniCare appointments were cancelled due to a banned account",
                    buildBannedAccountHtml(bannedUser, appointments, banExpiresAt));
        } catch (Exception ex) {
            logBanEmailFailure(to, bannedUser, ex);
        }
    }

    /**
     * CANCELLED because of a ban: email the unaffected counterparty (a doctor
     * when the patient was banned, or a patient when the doctor was banned)
     * once, listing only the appointments they shared with the banned account.
     */
    public void notifyAffectedCounterpartyCancellations(User affectedUser,
                                                        List<Appointment> appointments,
                                                        LocalDateTime banExpiresAt) {
        if (appointments == null || appointments.isEmpty()) {
            return;
        }
        String to = affectedUser.getEmail();
        try {
            emailService.sendHtmlMessage(to,
                    "Appointments cancelled due to a banned account",
                    buildAffectedCounterpartyHtml(affectedUser, appointments, banExpiresAt));
        } catch (Exception ex) {
            logBanEmailFailure(to, affectedUser, ex);
        }
    }

    private String buildBannedAccountHtml(User bannedUser, List<Appointment> appointments,
                                          LocalDateTime banExpiresAt) {
        return EmailTemplate.titled("Your appointments were cancelled due to a banned account")
                .message("Hello " + bannedUser.getFirstName() + ", your CliniCare account has been temporarily "
                        + "banned by an administrator. The appointment(s) below were automatically cancelled "
                        + "because the account was banned during its scheduled appointment period.")
                .table(APPOINTMENT_TABLE_HEADERS, appointmentRows(appointments))
                .action("You will be able to use CliniCare again once the ban ends on "
                        + banExpiresAt.format(DATE_FMT) + ", and you can then book new appointments as usual. "
                        + "These cancelled appointments will not be reinstated.")
                .render();
    }

    private String buildAffectedCounterpartyHtml(User affectedUser, List<Appointment> appointments,
                                                 LocalDateTime banExpiresAt) {
        return EmailTemplate.titled("Appointments cancelled due to a banned account")
                .message("Hello " + affectedUser.getFirstName() + ", a participant's CliniCare account has been "
                        + "temporarily banned. The appointment(s) below with them were automatically cancelled "
                        + "because the account was banned during its scheduled appointment period.")
                .table(APPOINTMENT_TABLE_HEADERS, appointmentRows(appointments))
                .action("No action is required from you. These appointments will not be reinstated.")
                .render();
    }

    /** Renders the affected appointments as the rows of a single summary table. */
    private List<List<String>> appointmentRows(List<Appointment> appointments) {
        return appointments.stream()
                .map(a -> List.of(
                        patientName(a),
                        "Dr. " + doctorLastName(a),
                        a.getAppointmentDate().format(DATE_FMT),
                        a.getAppointmentTime().format(TIME_FMT)))
                .toList();
    }

    private boolean alreadyNotified(Appointment appointment, AppointmentStatus status) {
        return appointment.getLastNotifiedStatus() == status;
    }

    private void markNotified(Appointment appointment, AppointmentStatus status) {
        appointment.setLastNotifiedStatus(status);
        appointmentRepository.save(appointment);
    }

    private void logEmailFailure(Appointment appointment, AppointmentStatus status,
                                  String recipient, Exception ex) {
        // Log safely: never include credentials. The appointment id is internal
        // and only used here for diagnostics, never placed in the email body.
        Long appointmentId = appointment.getId();
        log.error("Failed to send {} appointment notification to {} (appointment id={}): {}",
                status, recipient, appointmentId, ex.getMessage());
    }

    private void logBanEmailFailure(String recipient, User user, Exception ex) {
        // Log safely: never include credentials. Only the recipient address and
        // the internal user id are recorded for diagnostics.
        log.error("Failed to send ban-related appointment cancellation email to {} (user id={}): {}",
                recipient, user.getId(), ex.getMessage());
    }

    // ---------------------------------------------------------------------------
    // Email content builders
    // ---------------------------------------------------------------------------

    private String buildRequestedHtml(Appointment a) {
        return withDetails(EmailTemplate.titled("New appointment request")
                .message("Hello Dr. " + doctorLastName(a) + ", a new appointment request from "
                        + patientName(a) + " is waiting for your review."), a, null)
                .action("Please confirm or decline this request from your CliniCare dashboard. "
                        + "The patient has been notified that their request is pending.")
                .render();
    }

    private String buildConfirmedPatientHtml(Appointment a) {
        return withDetails(EmailTemplate.titled("Your appointment is confirmed")
                .message("Hello " + a.getPatient().getUser().getFirstName() + ", your appointment with Dr. "
                        + doctorLastName(a) + " has been confirmed."), a, null)
                .action("No further action is needed. Please arrive a few minutes early. You can view or cancel "
                        + "this appointment at any time from your CliniCare dashboard.")
                .render();
    }

    private String buildConfirmedDoctorHtml(Appointment a) {
        return withDetails(EmailTemplate.titled("Appointment confirmed")
                .message("Hello Dr. " + doctorLastName(a) + ", the appointment with " + patientName(a)
                        + " has been confirmed."), a, null)
                .action("This appointment is now on your schedule. You can review it from your "
                        + "CliniCare dashboard.")
                .render();
    }

    private String buildRefusedHtml(Appointment a) {
        return withDetails(EmailTemplate.titled("Your appointment request was declined")
                .message("Hello " + a.getPatient().getUser().getFirstName() + ", your appointment request with Dr. "
                        + doctorLastName(a) + " for " + a.getAppointmentDate().format(DATE_FMT) + " at "
                        + a.getAppointmentTime().format(TIME_FMT) + " was declined."), a, null)
                .action("You can request a new appointment with a different date or another doctor from your "
                        + "CliniCare dashboard.")
                .render();
    }

    private String buildCancelledHtml(Appointment a, String reason, boolean doctorPerspective) {
        String when = a.getAppointmentDate().format(DATE_FMT) + " at " + a.getAppointmentTime().format(TIME_FMT);
        String message = doctorPerspective
                ? "Hello Dr. " + doctorLastName(a) + ", the appointment with " + patientName(a)
                        + " scheduled for " + when + " has been cancelled."
                : "Hello " + a.getPatient().getUser().getFirstName() + ", your appointment with Dr. "
                        + doctorLastName(a) + " scheduled for " + when + " has been cancelled.";
        String title = doctorPerspective ? "Appointment cancelled" : "Your appointment has been cancelled";
        String next = doctorPerspective
                ? "No further action is required."
                : "If you still need care, please request a new appointment from your CliniCare dashboard.";
        return withDetails(EmailTemplate.titled(title).message(message), a, reason)
                .action(next)
                .render();
    }

    /** Adds the human-readable appointment details; no internal database id is ever included. */
    private EmailTemplate.Builder withDetails(EmailTemplate.Builder builder, Appointment a, String reason) {
        String specialty = a.getDoctor().getSpecialty();
        return builder
                .detail("Patient", patientName(a))
                .detail("Doctor", "Dr. " + doctorLastName(a)
                        + (specialty != null && !specialty.isBlank() ? " (" + specialty + ")" : ""))
                .detail("Date", a.getAppointmentDate().format(DATE_FMT))
                .detail("Time", a.getAppointmentTime().format(TIME_FMT))
                .detail("Reason", a.getReason())
                .detail("Cancellation reason", reason);
    }

    private static String patientName(Appointment a) {
        return fullName(a.getPatient().getUser());
    }

    private static String doctorLastName(Appointment a) {
        return a.getDoctor().getUser().getLastName();
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
