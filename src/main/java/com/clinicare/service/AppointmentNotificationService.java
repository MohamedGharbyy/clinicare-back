package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

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

    // ---------------------------------------------------------------------------
    // Email content builders
    // ---------------------------------------------------------------------------

    private String buildRequestedHtml(Appointment a) {
        String intro = "Hello Dr. " + escapeHtml(doctorLastName(a)) + ",<br/>"
                + "A new appointment request from <strong>" + escapeHtml(patientName(a))
                + "</strong> is waiting for your review.";
        String next = "Please confirm or decline this request from your CliniCare dashboard. "
                + "The patient has been notified that their request is pending.";
        return wrap("New appointment request", intro, detailsRows(a, null), next);
    }

    private String buildConfirmedPatientHtml(Appointment a) {
        String intro = "Hello " + escapeHtml(a.getPatient().getUser().getFirstName()) + ",<br/>"
                + "Your appointment with <strong>Dr. " + escapeHtml(doctorLastName(a))
                + "</strong> has been confirmed.";
        String next = "No further action is needed. Please arrive a few minutes early. "
                + "You can view or cancel this appointment at any time from your CliniCare dashboard.";
        return wrap("Your appointment is confirmed", intro, detailsRows(a, null), next);
    }

    private String buildConfirmedDoctorHtml(Appointment a) {
        String intro = "Hello Dr. " + escapeHtml(doctorLastName(a)) + ",<br/>"
                + "The appointment with <strong>" + escapeHtml(patientName(a))
                + "</strong> has been confirmed.";
        String next = "This appointment is now on your schedule. "
                + "You can review it from your CliniCare dashboard.";
        return wrap("Appointment confirmed", intro, detailsRows(a, null), next);
    }

    private String buildRefusedHtml(Appointment a) {
        String intro = "Hello " + escapeHtml(a.getPatient().getUser().getFirstName()) + ",<br/>"
                + "We are sorry to let you know that your appointment request with <strong>Dr. "
                + escapeHtml(doctorLastName(a)) + "</strong> for <strong>"
                + escapeHtml(a.getAppointmentDate().format(DATE_FMT)) + " at "
                + escapeHtml(a.getAppointmentTime().format(TIME_FMT)) + "</strong> was declined.";
        String next = "You can request a new appointment with a different date or another doctor "
                + "from your CliniCare dashboard.";
        return wrap("Your appointment request was declined", intro, detailsRows(a, null), next);
    }

    private String buildCancelledHtml(Appointment a, String reason, boolean doctorPerspective) {
        String intro;
        if (doctorPerspective) {
            intro = "Hello Dr. " + escapeHtml(doctorLastName(a)) + ",<br/>"
                    + "The appointment with <strong>" + escapeHtml(patientName(a))
                    + "</strong> scheduled for <strong>"
                    + escapeHtml(a.getAppointmentDate().format(DATE_FMT)) + " at "
                    + escapeHtml(a.getAppointmentTime().format(TIME_FMT)) + "</strong> has been cancelled.";
        } else {
            intro = "Hello " + escapeHtml(a.getPatient().getUser().getFirstName()) + ",<br/>"
                    + "Your appointment with <strong>Dr. " + escapeHtml(doctorLastName(a))
                    + "</strong> scheduled for <strong>"
                    + escapeHtml(a.getAppointmentDate().format(DATE_FMT)) + " at "
                    + escapeHtml(a.getAppointmentTime().format(TIME_FMT)) + "</strong> has been cancelled.";
        }
        String next = doctorPerspective
                ? "No further action is required."
                : "If you still need care, please request a new appointment from your CliniCare dashboard.";
        return wrap(doctorPerspective ? "Appointment cancelled" : "Your appointment has been cancelled",
                intro, detailsRows(a, reason), next);
    }

    private String detailsRows(Appointment a, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append(row("Patient", patientName(a)));
        sb.append(row("Doctor", "Dr. " + doctorLastName(a)
                + (a.getDoctor().getSpecialty() != null
                ? " (" + a.getDoctor().getSpecialty() + ")" : "")));
        sb.append(row("Date", a.getAppointmentDate().format(DATE_FMT)));
        sb.append(row("Time", a.getAppointmentTime().format(TIME_FMT)));
        if (a.getReason() != null && !a.getReason().isBlank()) {
            sb.append(row("Reason", a.getReason()));
        }
        if (reason != null && !reason.isBlank()) {
            sb.append(row("Cancellation reason", reason));
        }
        return sb.toString();
    }

    private String row(String label, String value) {
        return "<tr>"
                + "<td style=\"padding:8px 12px;border-bottom:1px solid #eef2f7;color:#6b7280;width:42%;"
                + "font-size:14px;\">" + escapeHtml(label) + "</td>"
                + "<td style=\"padding:8px 12px;border-bottom:1px solid #eef2f7;color:#111827;"
                + "font-weight:600;font-size:14px;\">" + escapeHtml(value) + "</td>"
                + "</tr>";
    }

    private String wrap(String heading, String introHtml, String detailsHtml, String nextStepHtml) {
        String next = nextStepHtml == null ? "" :
                "<p style=\"margin:24px 0 0;line-height:1.5;color:#374151;\">" + nextStepHtml + "</p>";
        return """
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
                          <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a;">__HEADING__</h1>
                          <p style="margin:0 0 20px;line-height:1.5;color:#374151;">__INTRO__</p>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                 style="border:1px solid #eef2f7;border-radius:8px;overflow:hidden;">__DETAILS__</table>
                          __NEXT__
                          <p style="margin:24px 0 0;line-height:1.5;color:#6b7280;font-size:13px;">
                            If you have any questions, contact CliniCare Support from your dashboard.
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
                """
                .replace("__HEADING__", escapeHtml(heading))
                .replace("__INTRO__", introHtml)
                .replace("__DETAILS__", detailsHtml)
                .replace("__NEXT__", next);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
