package com.clinicare.service;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.CancellationReason;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cancels appointments that are affected when a patient or doctor is banned and
 * notifies the relevant parties.
 * <p>
 * When a user is banned, every appointment they are part of that is still in the
 * future <em>and</em> falls within the ban window (i.e. the scheduled time is
 * before the ban expires) is moved to {@link AppointmentStatus#CANCELLED} with
 * the reason {@link CancellationReason#ACCOUNT_BANNED}. Terminal appointments
 * (already {@code COMPLETED}, {@code REJECTED}, or {@code CANCELLED}) are never
 * touched.
 * <p>
 * Notifications are sent immediately, not on the appointment date:
 * <ul>
 *   <li>For a banned <b>patient</b>: the patient is notified once, and each
 *       affected doctor is notified once, consolidating all of their shared
 *       appointments into a single email.</li>
 *   <li>For a banned <b>doctor</b>: the doctor is notified once, and each
 *       affected patient is notified once, consolidating their shared
 *       appointments into a single email.</li>
 * </ul>
 * Emails are sent through {@link AppointmentNotificationService}, which isolates
 * delivery failures: a failed email can never roll back the ban or the
 * cancellations performed here.
 */
@Service
public class BanAppointmentCancellationService {

    /** Statuses that may still be cancelled because of a ban. */
    private static final Set<AppointmentStatus> CANCELABLE_STATUSES = EnumSet.of(
            AppointmentStatus.PENDING,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS);

    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentNotificationService notificationService;

    public BanAppointmentCancellationService(AppointmentRepository appointmentRepository,
                                              PatientProfileRepository patientProfileRepository,
                                              DoctorProfileRepository doctorProfileRepository,
                                              AppointmentNotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.notificationService = notificationService;
    }

    /**
     * Cancels and notifies for the supplied banned user.
     *
     * @param user         the banned account (patient or doctor)
     * @param banStart     when the ban took effect (used as the "future" reference)
     * @param banExpiresAt when the ban lifts (appointments before this are affected)
     * @return the appointments that were cancelled
     */
    public List<Appointment> cancelForBannedUser(User user,
                                                 LocalDateTime banStart,
                                                 LocalDateTime banExpiresAt) {
        if (user == null) {
            return List.of();
        }
        return switch (user.getRole()) {
            case PATIENT -> cancelForPatient(user, banStart, banExpiresAt);
            case DOCTOR -> cancelForDoctor(user, banStart, banExpiresAt);
            default -> List.of();
        };
    }

    private List<Appointment> cancelForPatient(User patientUser,
                                               LocalDateTime banStart,
                                               LocalDateTime banExpiresAt) {
        Optional<PatientProfile> profileOpt = patientProfileRepository.findByUser(patientUser);
        if (profileOpt.isEmpty()) {
            return List.of();
        }
        List<Appointment> affected = findAffected(
                appointmentRepository.findByPatient(profileOpt.get()), banStart, banExpiresAt);
        if (affected.isEmpty()) {
            return List.of();
        }

        List<Appointment> cancelled = cancelAll(affected);

        // The banned patient is notified once about all of their cancelled appointments.
        notificationService.notifyBannedAccountCancellations(patientUser, cancelled, banExpiresAt);

        // Each affected doctor receives a single consolidated email.
        for (List<Appointment> doctorAppointments : groupByDoctor(cancelled).values()) {
            User doctorUser = doctorAppointments.get(0).getDoctor().getUser();
            notificationService.notifyAffectedCounterpartyCancellations(
                    doctorUser, doctorAppointments, banExpiresAt);
        }
        return cancelled;
    }

    private List<Appointment> cancelForDoctor(User doctorUser,
                                              LocalDateTime banStart,
                                              LocalDateTime banExpiresAt) {
        Optional<DoctorProfile> profileOpt = doctorProfileRepository.findByUser(doctorUser);
        if (profileOpt.isEmpty()) {
            return List.of();
        }
        List<Appointment> affected = findAffected(
                appointmentRepository.findByDoctor(profileOpt.get()), banStart, banExpiresAt);
        if (affected.isEmpty()) {
            return List.of();
        }

        List<Appointment> cancelled = cancelAll(affected);

        // The banned doctor is notified once about all of their cancelled appointments.
        notificationService.notifyBannedAccountCancellations(doctorUser, cancelled, banExpiresAt);

        // Each affected patient receives a single consolidated email.
        for (List<Appointment> patientAppointments : groupByPatient(cancelled).values()) {
            User patientUser = patientAppointments.get(0).getPatient().getUser();
            notificationService.notifyAffectedCounterpartyCancellations(
                    patientUser, patientAppointments, banExpiresAt);
        }
        return cancelled;
    }

    private List<Appointment> cancelAll(List<Appointment> affected) {
        List<Appointment> cancelled = new ArrayList<>();
        for (Appointment appointment : affected) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancellationReason(CancellationReason.ACCOUNT_BANNED);
            cancelled.add(appointmentRepository.save(appointment));
        }
        return cancelled;
    }

    private Map<Long, List<Appointment>> groupByDoctor(List<Appointment> appointments) {
        Map<Long, List<Appointment>> byDoctor = new LinkedHashMap<>();
        for (Appointment appointment : appointments) {
            Long key = appointment.getDoctor().getUser().getId();
            byDoctor.computeIfAbsent(key, k -> new ArrayList<>()).add(appointment);
        }
        return byDoctor;
    }

    private Map<Long, List<Appointment>> groupByPatient(List<Appointment> appointments) {
        Map<Long, List<Appointment>> byPatient = new LinkedHashMap<>();
        for (Appointment appointment : appointments) {
            Long key = appointment.getPatient().getUser().getId();
            byPatient.computeIfAbsent(key, k -> new ArrayList<>()).add(appointment);
        }
        return byPatient;
    }

    /**
     * Selects appointments that are still cancellable, scheduled in the future
     * (after the ban starts), and within the ban window (before it expires).
     */
    private List<Appointment> findAffected(List<Appointment> appointments,
                                          LocalDateTime banStart,
                                          LocalDateTime banExpiresAt) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment appointment : appointments) {
            if (!CANCELABLE_STATUSES.contains(appointment.getStatus())) {
                continue;
            }
            LocalDateTime scheduled = LocalDateTime.of(
                    appointment.getAppointmentDate(), appointment.getAppointmentTime());
            if (scheduled.isAfter(banStart) && scheduled.isBefore(banExpiresAt)) {
                result.add(appointment);
            }
        }
        return result;
    }
}
