package com.clinicare.service;

import com.clinicare.dto.AdminDashboardResponseDTO;
import com.clinicare.dto.AdminDoctorResponseDTO;
import com.clinicare.dto.AdminPatientResponseDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.dto.PrescriptionMedicationResponseDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Prescription;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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

    private static final Comparator<Appointment> APPOINTMENT_BY_DATE_TIME = Comparator
            .comparing((Appointment a) -> LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()));

    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    public AdminService(PatientProfileRepository patientProfileRepository,
                        DoctorProfileRepository doctorProfileRepository,
                        AppointmentRepository appointmentRepository,
                        PrescriptionRepository prescriptionRepository) {
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
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