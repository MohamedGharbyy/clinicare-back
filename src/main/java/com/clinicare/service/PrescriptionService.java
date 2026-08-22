package com.clinicare.service;

import com.clinicare.dto.PrescriptionMedicationResponseDTO;
import com.clinicare.dto.PrescriptionRequestDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Prescription;
import com.clinicare.entity.PrescriptionMedication;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.PrescriptionMedicationRepository;
import com.clinicare.repository.PrescriptionRepository;
import com.clinicare.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Service layer for managing prescriptions and their prescribed medications.
 * <p>
 * All doctor-scoped operations derive the acting doctor from the authenticated
 * user. Patient-scoped operations derive the patient the same way. The service
 * enforces that a doctor can only prescribe for patients with whom they have
 * an existing appointment relationship.
 */
@Service
public class PrescriptionService {

    private static final Comparator<Prescription> BY_CREATION_DATE = Comparator
            .comparing(Prescription::getCreationDate);

    private static final java.util.Set<AppointmentStatus> RELATIONSHIP_STATUSES =
            java.util.Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicationRepository prescriptionMedicationRepository;
    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final Validator validator;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
                               PrescriptionMedicationRepository prescriptionMedicationRepository,
                               UserRepository userRepository,
                               PatientProfileRepository patientProfileRepository,
                               DoctorProfileRepository doctorProfileRepository,
                               AppointmentRepository appointmentRepository,
                               Validator validator) {
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicationRepository = prescriptionMedicationRepository;
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.validator = validator;
    }

    /**
     * Creates a new prescription for the authenticated doctor.
     * <p>
     * The doctor is always derived from the authenticated user. The referenced
     * patient must exist, and there must be at least one prior appointment
     * between the doctor and patient to establish a legitimate relationship.
     * The prescription must contain at least one medication.
     */
    @Transactional
    public PrescriptionResponseDTO createPrescription(PrescriptionRequestDTO request) {
        validateRequest(request);

        DoctorProfile doctor = requireCurrentDoctor();

        PatientProfile patient = patientProfileRepository.findById(request.patientId())
                .orElseThrow(() -> new BadRequestException("Patient not found"));

        if (!appointmentRepository.existsByPatientAndDoctorAndStatusIn(patient, doctor, RELATIONSHIP_STATUSES)) {
            throw new BadRequestException(
                    "You can only prescribe for patients with whom you have an existing appointment relationship");
        }

        Prescription prescription = new Prescription();
        prescription.setDoctor(doctor);
        prescription.setPatient(patient);

        Prescription savedPrescription = prescriptionRepository.save(prescription);

        List<PrescriptionMedication> medications = request.medications().stream()
                .map(med -> {
                    PrescriptionMedication medication = new PrescriptionMedication();
                    medication.setPrescription(savedPrescription);
                    medication.setMedicationName(med.medicationName());
                    medication.setDosage(med.dosage());
                    medication.setFrequency(med.frequency());
                    medication.setDuration(med.duration());
                    medication.setInstructions(med.instructions());
                    return medication;
                })
                .toList();

        prescriptionMedicationRepository.saveAll(medications);
        savedPrescription.setMedications(medications);

        return toResponse(savedPrescription);
    }

    /**
     * Returns all prescriptions created by the authenticated doctor,
     * ordered chronologically by creation date.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponseDTO> getMyCreatedPrescriptions() {
        DoctorProfile doctor = requireCurrentDoctor();
        return prescriptionRepository.findByDoctor(doctor).stream()
                .sorted(BY_CREATION_DATE)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns all prescriptions for a specific patient that were created by
     * the authenticated doctor, ordered chronologically by creation date.
     * <p>
     * The doctor must have an existing appointment relationship with the
     * patient to view their prescriptions.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponseDTO> getPatientPrescriptions(Long patientId) {
        DoctorProfile doctor = requireCurrentDoctor();

        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new BadRequestException("Patient not found"));

        if (!appointmentRepository.existsByPatientAndDoctorAndStatusIn(patient, doctor, RELATIONSHIP_STATUSES)) {
            throw new BadRequestException(
                    "You can only view prescriptions for patients with whom you have an existing appointment relationship");
        }

        return prescriptionRepository.findByDoctorAndPatient(doctor, patient).stream()
                .sorted(BY_CREATION_DATE)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns all prescriptions belonging to the authenticated patient,
     * ordered chronologically by creation date.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponseDTO> getMyPrescriptions() {
        PatientProfile patient = requireCurrentPatient();
        return prescriptionRepository.findByPatient(patient).stream()
                .sorted(BY_CREATION_DATE)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns all prescriptions across the platform, ordered chronologically
     * by creation date. Intended for administrative review.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponseDTO> getAllPrescriptions() {
        return prescriptionRepository.findAll().stream()
                .sorted(BY_CREATION_DATE)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Runs Bean Validation on the request payload so that required-field and
     * length constraints are enforced at the service layer.
     */
    private void validateRequest(PrescriptionRequestDTO request) {
        Set<ConstraintViolation<PrescriptionRequestDTO>> violations = validator.validate(request);
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
        return userRepository.findByEmailAndStatusNot(email, AccountStatus.DELETED)
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
     * Maps a {@link Prescription} entity to its DTO representation. Must be
     * called within an open transaction so that the lazy medication
     * associations can be loaded.
     */
    private PrescriptionResponseDTO toResponse(Prescription prescription) {
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
