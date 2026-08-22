package com.clinicare.service;

import com.clinicare.dto.MedicalReportRequestDTO;
import com.clinicare.dto.MedicalReportResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.MedicalReport;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.MedicalReportRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class MedicalReportService {

    private static final Comparator<MedicalReport> BY_REPORT_DATE = Comparator
            .comparing(MedicalReport::getReportDate)
            .thenComparing(MedicalReport::getCreatedAt);

    private static final java.util.Set<AppointmentStatus> RELATIONSHIP_STATUSES =
            java.util.Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final MedicalReportRepository medicalReportRepository;
    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final Validator validator;

    public MedicalReportService(MedicalReportRepository medicalReportRepository,
                                UserRepository userRepository,
                                PatientProfileRepository patientProfileRepository,
                                DoctorProfileRepository doctorProfileRepository,
                                AppointmentRepository appointmentRepository,
                                Validator validator) {
        this.medicalReportRepository = medicalReportRepository;
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.validator = validator;
    }

    @Transactional
    public MedicalReportResponseDTO createMedicalReport(MedicalReportRequestDTO request) {
        validateRequest(request);

        DoctorProfile doctor = requireCurrentDoctor();

        PatientProfile patient = patientProfileRepository.findById(request.patientId())
                .orElseThrow(() -> new BadRequestException("Patient not found"));

        if (!appointmentRepository.existsByPatientAndDoctorAndStatusIn(patient, doctor, RELATIONSHIP_STATUSES)) {
            throw new BadRequestException(
                    "You can only create reports for patients with whom you have an existing appointment relationship");
        }

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElse(null);
            if (appointment != null) {
                if (!appointment.getPatient().getId().equals(patient.getId())) {
                    throw new BadRequestException("Appointment does not belong to the specified patient");
                }
                if (!appointment.getDoctor().getId().equals(doctor.getId())) {
                    throw new BadRequestException("Appointment does not belong to you");
                }
            }
        }

        MedicalReport report = new MedicalReport();
        report.setPatient(patient);
        report.setDoctor(doctor);
        report.setAppointment(appointment);
        report.setDiagnosis(request.diagnosis());
        report.setSymptoms(request.symptoms());
        report.setNotes(request.notes());
        report.setReportDate(request.reportDate());

        MedicalReport saved = medicalReportRepository.save(report);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MedicalReportResponseDTO> getMyCreatedReports() {
        DoctorProfile doctor = requireCurrentDoctor();
        return medicalReportRepository.findByDoctor(doctor).stream()
                .sorted(BY_REPORT_DATE)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalReportResponseDTO> getPatientReports(Long patientId) {
        DoctorProfile doctor = requireCurrentDoctor();

        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new BadRequestException("Patient not found"));

        if (!appointmentRepository.existsByPatientAndDoctorAndStatusIn(patient, doctor, RELATIONSHIP_STATUSES)) {
            throw new BadRequestException(
                    "You can only view reports for patients with whom you have an existing appointment relationship");
        }

        return medicalReportRepository.findByDoctorAndPatient(doctor, patient).stream()
                .sorted(BY_REPORT_DATE)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalReportResponseDTO> getMyReports() {
        PatientProfile patient = requireCurrentPatient();
        return medicalReportRepository.findByPatient(patient).stream()
                .sorted(BY_REPORT_DATE)
                .map(this::toResponse)
                .toList();
    }

    private void validateRequest(MedicalReportRequestDTO request) {
        Set<ConstraintViolation<MedicalReportRequestDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<String> messages = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .toList();
            throw new BadRequestException(String.join("; ", messages));
        }
    }

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

    private PatientProfile requireCurrentPatient() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.PATIENT) {
            throw new BadRequestException("Only patients can perform this operation");
        }
        return patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Patient profile not found"));
    }

    private DoctorProfile requireCurrentDoctor() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.DOCTOR) {
            throw new BadRequestException("Only doctors can perform this operation");
        }
        return doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
    }

    private MedicalReportResponseDTO toResponse(MedicalReport report) {
        Long appointmentId = report.getAppointment() != null ? report.getAppointment().getId() : null;
        LocalDate appointmentDate = report.getAppointment() != null ? report.getAppointment().getAppointmentDate() : null;
        return new MedicalReportResponseDTO(
                report.getId(),
                report.getPatient().getId(),
                fullName(report.getPatient().getUser()),
                report.getDoctor().getId(),
                fullName(report.getDoctor().getUser()),
                appointmentId,
                appointmentDate,
                report.getDiagnosis(),
                report.getSymptoms(),
                report.getNotes(),
                report.getReportDate(),
                report.getCreatedAt());
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
