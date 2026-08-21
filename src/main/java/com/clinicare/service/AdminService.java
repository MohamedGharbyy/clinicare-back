package com.clinicare.service;

import com.clinicare.dto.AdminDashboardResponseDTO;
import com.clinicare.dto.AdminDoctorResponseDTO;
import com.clinicare.dto.AdminPatientResponseDTO;
import com.clinicare.dto.AdminUserResponseDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.dto.PrescriptionMedicationResponseDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Prescription;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.PrescriptionRepository;
import com.clinicare.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for the admin dashboard.
 * <p>
 * Backed by real repositories; summary counters use {@code count()},
 * management lists map entities to admin-safe DTOs, and administrative
 * actions like appointment cancellation are supported with proper validation.
 */
@Service
public class AdminService {

    private static final Comparator<PatientProfile> PATIENT_BY_NAME = Comparator
            .comparing((PatientProfile p) -> p.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(p -> p.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<DoctorProfile> DOCTOR_BY_NAME = Comparator
            .comparing((DoctorProfile d) -> d.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(d -> d.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<User> USER_BY_NAME = Comparator
            .comparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(User::getLastName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<Appointment> APPOINTMENT_BY_DATE_TIME = Comparator
            .comparing((Appointment a) -> LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()));

    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final AppointmentNotificationService notificationService;
    private final BanAppointmentCancellationService banCancellationService;

    public AdminService(PatientProfileRepository patientProfileRepository,
                        DoctorProfileRepository doctorProfileRepository,
                        AppointmentRepository appointmentRepository,
                        PrescriptionRepository prescriptionRepository,
                        UserRepository userRepository,
                        AppointmentNotificationService notificationService,
                        BanAppointmentCancellationService banCancellationService) {
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.banCancellationService = banCancellationService;
    }

    /** Aggregated counters for the dashboard summary cards. */
    @Transactional(readOnly = true)
    public AdminDashboardResponseDTO getDashboardSummary() {
        return new AdminDashboardResponseDTO(
                patientProfileRepository.count(),
                doctorProfileRepository.count(),
                appointmentRepository.count());
    }

    /** All registered patients, ordered by name. */
    @Transactional(readOnly = true)
    public List<AdminPatientResponseDTO> listPatients() {
        return patientProfileRepository.findAll().stream()
                .sorted(PATIENT_BY_NAME)
                .map(AdminService::toPatientResponse)
                .toList();
    }

    /** All registered doctors, ordered by name. */
    @Transactional(readOnly = true)
    public List<AdminDoctorResponseDTO> listDoctors() {
        return doctorProfileRepository.findAll().stream()
                .sorted(DOCTOR_BY_NAME)
                .map(AdminService::toDoctorResponse)
                .toList();
    }

    /** All appointments across the platform, ordered chronologically by date/time. */
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> listAppointments() {
        return appointmentRepository.findAll().stream()
                .sorted(APPOINTMENT_BY_DATE_TIME)
                .map(AdminService::toAppointmentResponse)
                .toList();
    }

    /** All prescriptions across the platform, ordered chronologically by creation date. */
    @Transactional(readOnly = true)
    public List<PrescriptionResponseDTO> listAllPrescriptions() {
        return prescriptionRepository.findAll().stream()
                .sorted(Comparator.comparing(Prescription::getCreationDate))
                .map(AdminService::toPrescriptionResponse)
                .toList();
    }

    /**
     * Returns every managed PATIENT/DOCTOR account. By default deleted accounts
     * are excluded; pass {@code includeDeleted=true} to also return soft-deleted
     * accounts (useful for an Admin "recycle bin" view). Expired bans are
     * reconciled to {@code ACTIVE} before being returned.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponseDTO> listAccounts(boolean includeDeleted) {
        Map<Long, String> adminEmails = loadAdminEmails();
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() != Role.ADMIN)
                .filter(user -> includeDeleted || user.getStatus() != AccountStatus.DELETED)
                .peek(User::reconcileBan)
                .sorted(USER_BY_NAME)
                .map(user -> toUserResponse(user, adminEmails))
                .toList();
    }

    /**
     * Soft-deletes an account: the user can no longer log in and is hidden from
     * the active list, but their appointments, prescriptions, and medical
     * reports remain intact. Admin accounts and the acting Admin's own account
     * are protected.
     */
    @Transactional
    public void deleteAccount(Long userId, Long adminId) {
        User user = requireManageable(userId, adminId, "delete");
        LocalDateTime now = LocalDateTime.now();
        user.setStatus(AccountStatus.DELETED);
        user.setDeletedAt(now);
        user.setDeletedById(adminId);
        user.setBanExpiresAt(null);
        userRepository.save(user);
    }

    /** Disables an account so the user cannot log in until re-enabled. */
    @Transactional
    public AdminUserResponseDTO disableAccount(Long userId, Long adminId) {
        User user = requireManageable(userId, adminId, "disable");
        user.setStatus(AccountStatus.DISABLED);
        user.setBanExpiresAt(null);
        return toUserResponse(userRepository.save(user), loadAdminEmails());
    }

    /** Re-enables a disabled account. */
    @Transactional
    public AdminUserResponseDTO enableAccount(Long userId, Long adminId) {
        User user = requireManageable(userId, adminId, "enable");
        user.setStatus(AccountStatus.ACTIVE);
        user.setBanExpiresAt(null);
        return toUserResponse(userRepository.save(user), loadAdminEmails());
    }

    /** Temporarily bans an account for the given number of days. */
    @Transactional
    public AdminUserResponseDTO banAccount(Long userId, int durationDays, Long adminId) {
        if (durationDays <= 0) {
            throw new BadRequestException("Ban duration must be a positive number of days");
        }
        User user = requireManageable(userId, adminId, "ban");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpiresAt = now.plusDays(durationDays);
        user.setStatus(AccountStatus.BANNED);
        user.setBanExpiresAt(banExpiresAt);
        user.setDeletedAt(null);
        user.setDeletedById(null);
        User saved = userRepository.save(user);

        // Cancel the user's future appointments that fall within the ban window and
        // notify the affected parties. Email delivery happens through the notification
        // service, which isolates failures: a failed email can never roll back the
        // ban or the appointment cancellations committed above.
        banCancellationService.cancelForBannedUser(saved, now, banExpiresAt);

        return toUserResponse(saved, loadAdminEmails());
    }

    private User requireManageable(Long userId, Long adminId, String action) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be " + action + "d");
        }
        if (user.getId().equals(adminId)) {
            throw new BadRequestException("You cannot " + action + " your own account");
        }
        if (user.getStatus() == AccountStatus.DELETED) {
            throw new BadRequestException("This account has already been deleted");
        }
        return user;
    }

    private Map<Long, String> loadAdminEmails() {
        return userRepository.findByRole(Role.ADMIN).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
    }

    private AdminUserResponseDTO toUserResponse(User user, Map<Long, String> adminEmails) {
        Optional<PatientProfile> patient = user.getRole() == Role.PATIENT
                ? patientProfileRepository.findByUserId(user.getId())
                : Optional.empty();
        Optional<DoctorProfile> doctor = user.getRole() == Role.DOCTOR
                ? doctorProfileRepository.findByUserId(user.getId())
                : Optional.empty();

        String specialty = doctor.map(DoctorProfile::getSpecialty).orElse(null);
        String phoneNumber = patient.map(PatientProfile::getPhoneNumber)
                .orElseGet(() -> doctor.map(DoctorProfile::getPhoneNumber).orElse(null));

        String deletedByEmail = user.getDeletedById() != null ? adminEmails.get(user.getDeletedById()) : null;

        return new AdminUserResponseDTO(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getBanExpiresAt(),
                user.getCreatedAt(),
                specialty,
                phoneNumber,
                user.getDeletedAt(),
                user.getDeletedById(),
                deletedByEmail);
    }

    /**
     * Cancels an appointment on behalf of the clinic.
     * <p>
     * Only appointments in {@link AppointmentStatus#PENDING} or
     * {@link AppointmentStatus#CONFIRMED} status can be cancelled.
     *
     * @param appointmentId the appointment ID to cancel
     * @return the updated appointment representation
     */
    @Transactional
    public AppointmentResponseDTO cancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BadRequestException("Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException(
                    "This appointment cannot be cancelled in its current status: " + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        // CANCELLED: notify both the patient and the doctor (clinic-initiated).
        notificationService.notifyCancelled(saved, "Cancelled by the clinic.");
        return toAppointmentResponse(saved);
    }

    private static AdminPatientResponseDTO toPatientResponse(PatientProfile patient) {
        return new AdminPatientResponseDTO(
                patient.getId(),
                patient.getUser().getFirstName() + " " + patient.getUser().getLastName(),
                patient.getUser().getEmail(),
                patient.getDateOfBirth(),
                patient.getPhoneNumber(),
                patient.getUser().getCreatedAt());
    }

    private static AdminDoctorResponseDTO toDoctorResponse(DoctorProfile doctor) {
        return new AdminDoctorResponseDTO(
                doctor.getId(),
                doctor.getUser().getFirstName() + " " + doctor.getUser().getLastName(),
                doctor.getUser().getEmail(),
                doctor.getSpecialty(),
                doctor.getLicenseNumber(),
                doctor.getPhoneNumber(),
                doctor.getUser().getCreatedAt());
    }

    private static AppointmentResponseDTO toAppointmentResponse(Appointment appointment) {
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

    private static PrescriptionResponseDTO toPrescriptionResponse(Prescription prescription) {
        List<PrescriptionMedicationResponseDTO> medications = prescription.getMedications().stream()
                .map(med -> new PrescriptionMedicationResponseDTO(
                        med.getId(),
                        med.getMedicationName(),
                        med.getDosage(),
                        med.getFrequency(),
                        med.getDuration(),
                        med.getInstructions()))
                .toList();

        return new PrescriptionResponseDTO(
                prescription.getId(),
                prescription.getDoctor().getId(),
                fullName(prescription.getDoctor().getUser()),
                prescription.getPatient().getId(),
                fullName(prescription.getPatient().getUser()),
                prescription.getCreationDate(),
                medications);
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}