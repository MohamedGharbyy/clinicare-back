package com.clinicare.service;

import com.clinicare.dto.AppointmentRequestDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Service layer for managing patient-doctor appointments.
 * <p>
 * Every patient-scoped operation derives the acting patient from the authenticated
 * user (the Spring Security principal) rather than trusting any patient id supplied
 * by the client. Doctor information is taken from the request only after validating
 * that the referenced doctor exists.
 */
@Service
public class AppointmentService {

    /** Statuses from which an appointment may still be cancelled. */
    private static final Set<AppointmentStatus> CANCELABLE_STATUSES =
            Set.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    /** Orders appointments chronologically by their date/time slot. */
    private static final Comparator<Appointment> BY_DATE_TIME = Comparator
            .comparing((Appointment a) -> LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()));

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final Validator validator;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              UserRepository userRepository,
                              PatientProfileRepository patientProfileRepository,
                              DoctorProfileRepository doctorProfileRepository,
                              Validator validator) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.validator = validator;
    }
/** Returns the current date/time in the application's JVM timezone. */
    private LocalDateTime nowInZone() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    /**
     * Background task that runs periodically to transition appointments
     * whose scheduled date/time has passed.
     * <p>
     * PENDING appointments become REJECTED.
     * CONFIRMED appointments become COMPLETED.
     * This ensures the pending-request count stays accurate without
     * requiring the frontend to be open.
     */
    @Scheduled(fixedRate = 60_000) // run every 60 seconds
    @Transactional
    public void transitionPastAppointments() {
        LocalDateTime now = nowInZone();

        // Find all PENDING appointments whose scheduled date/time is in the past
        List<Appointment> pendingPast = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                .filter(a -> {
                    LocalDateTime appointmentDateTime =
                            LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime());
                    return appointmentDateTime.isBefore(now);
                })
                .toList();

        // Transition each past-due PENDING appointment to REJECTED
        for (Appointment a : pendingPast) {
            a.setStatus(AppointmentStatus.REJECTED);
            appointmentRepository.save(a);
        }

        // Find all CONFIRMED appointments that were accepted but whose time has passed
        List<Appointment> confirmedPast = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> {
                    LocalDateTime appointmentDateTime =
                            LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime());
                    return appointmentDateTime.isBefore(now);
                })
                .toList();

        for (Appointment a : confirmedPast) {
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(a);
        }
    }

    /** Creates a new appointment request for the authenticated patient. */

    /**
     * Creates a new appointment request for the authenticated patient.
     * <p>
     * The patient is always derived from the authenticated user; any patient id
     * present on the request is ignored. New appointments always start with
     * {@link AppointmentStatus#PENDING}. The referenced doctor must exist, the
     * requested date/time must not be in the past, and all required fields must
     * be present and valid.
     */
    @Transactional
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO request) {
        validateRequest(request);

        PatientProfile patient = requireCurrentPatient();

        DoctorProfile doctor = doctorProfileRepository.findById(request.doctorId())
                .orElseThrow(() -> new BadRequestException("Doctor not found"));

        LocalDateTime requestedAt = LocalDateTime.of(request.appointmentDate(), request.appointmentTime());
        if (requestedAt.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Appointment date/time must not be in the past");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setAppointmentTime(request.appointmentTime());
        appointment.setReason(request.reason());
        appointment.setNotes(request.notes());
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment saved = appointmentRepository.save(appointment);
        return toResponse(saved);
    }

    /**
     * Returns all appointments belonging to the authenticated patient, ordered
     * chronologically.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getMyAppointments() {
        PatientProfile patient = requireCurrentPatient();
        return appointmentRepository.findByPatient(patient).stream()
                .sorted(BY_DATE_TIME)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns all appointments assigned to the authenticated doctor, ordered
     * chronologically.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getDoctorAppointments() {
        DoctorProfile doctor = requireCurrentDoctor();
        return appointmentRepository.findByDoctor(doctor).stream()
                .sorted(BY_DATE_TIME)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Accepts a pending appointment request assigned to the authenticated doctor.
     * <p>
     * Only the owning doctor may accept, and only while the request is still
     * {@link AppointmentStatus#PENDING}. The appointment is then marked
     * {@link AppointmentStatus#CONFIRMED} — the system's accepted state — and the
     * updated representation is returned.
     *
     * @param appointmentId the id of the appointment to accept
     * @return the accepted appointment with HTTP 200 OK
     */
    @Transactional
    public AppointmentResponseDTO acceptAppointment(Long appointmentId) {
        Appointment appointment = requireOwnPendingAppointment(appointmentId);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);
        return toResponse(saved);
    }

    /**
     * Rejects a pending appointment request assigned to the authenticated doctor.
     * <p>
     * Only the owning doctor may reject, and only while the request is still
     * {@link AppointmentStatus#PENDING}. The appointment is then marked
     * {@link AppointmentStatus#REJECTED} and the updated representation is returned.
     *
     * @param appointmentId the id of the appointment to reject
     * @return the rejected appointment with HTTP 200 OK
     */
    @Transactional
    public AppointmentResponseDTO rejectAppointment(Long appointmentId) {
        Appointment appointment = requireOwnPendingAppointment(appointmentId);
        appointment.setStatus(AppointmentStatus.REJECTED);
        Appointment saved = appointmentRepository.save(appointment);
        return toResponse(saved);
    }

    /** Resolves a PENDING appointment assigned to the authenticated doctor. */
    private Appointment requireOwnPendingAppointment(Long appointmentId) {
        DoctorProfile doctor = requireCurrentDoctor();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BadRequestException("Appointment not found"));
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new BadRequestException("You can only manage your own appointments");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException(
                    "This appointment cannot be updated in its current status: " + appointment.getStatus());
        }
        return appointment;
    }

    /**
     * Returns upcoming appointments for the authenticated patient, those whose
     * scheduled date/time is strictly in the future, ordered chronologically.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getUpcomingAppointments() {
        PatientProfile patient = requireCurrentPatient();
        LocalDateTime now = LocalDateTime.now();
        return appointmentRepository.findByPatient(patient).stream()
                .filter(a -> LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()).isAfter(now))
                .sorted(BY_DATE_TIME)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Cancels an appointment owned by the authenticated patient.
     * <p>
     * Only the owning patient may cancel, and only when the current status allows
     * it ({@link AppointmentStatus#PENDING} or {@link AppointmentStatus#CONFIRMED}).
     * The appointment is then marked {@link AppointmentStatus#CANCELLED}.
     */
    @Transactional
    public AppointmentResponseDTO cancelAppointment(Long appointmentId) {
        PatientProfile patient = requireCurrentPatient();

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BadRequestException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new BadRequestException("You can only cancel your own appointments");
        }
        if (!CANCELABLE_STATUSES.contains(appointment.getStatus())) {
            throw new BadRequestException(
                    "This appointment cannot be cancelled in its current status: " + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        return toResponse(saved);
    }

    /**
     * Runs Bean Validation on the request payload so that required-field and
     * length constraints are enforced at the service layer, even before a
     * controller is wired up with {@code @Valid}.
     */
    private void validateRequest(AppointmentRequestDTO request) {
        Set<ConstraintViolation<AppointmentRequestDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<String> messages = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .toList();
            throw new BadRequestException(String.join("; ", messages));
        }
    }

    /**
     * Resolves the {@link User} for the currently authenticated principal.
     * <p>
     * The {@code JwtAuthFilter} stores the user's email as the authentication
     * principal, so the principal is a {@code String} email.
     */
    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BadRequestException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        String email;
        if (principal instanceof String s) {
            email = s;
        } else {
            throw new BadRequestException("Unsupported authentication principal");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    /**
     * Resolves the {@link PatientProfile} belonging to the authenticated user,
     * requiring the caller to hold the {@link Role#PATIENT} role.
     */
    private PatientProfile requireCurrentPatient() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.PATIENT) {
            throw new BadRequestException("Only patients can perform this operation");
        }
        return patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Patient profile not found"));
    }

    /**
     * Resolves the {@link DoctorProfile} belonging to the authenticated user,
     * requiring the caller to hold the {@link Role#DOCTOR} role.
     */
    private DoctorProfile requireCurrentDoctor() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.DOCTOR) {
            throw new BadRequestException("Only doctors can perform this operation");
        }
        return doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
    }

    /**
     * Maps an {@link Appointment} entity to its DTO representation. Must be called
     * within an open transaction so that the lazy patient/doctor associations
     * (and their User back-references) can be loaded.
     */
    private AppointmentResponseDTO toResponse(Appointment appointment) {
        PatientProfile patient = appointment.getPatient();
        DoctorProfile doctor = appointment.getDoctor();
        return new AppointmentResponseDTO(
                appointment.getId(),
                patient.getId(),
                fullName(patient.getUser()),
                doctor.getId(),
                fullName(doctor.getUser()),
                doctor.getSpecialty(),
                appointment.getAppointmentDate(),
                appointment.getAppointmentTime(),
                appointment.getReason(),
                appointment.getNotes(),
                appointment.getStatus(),
                appointment.getCreatedAt());
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
