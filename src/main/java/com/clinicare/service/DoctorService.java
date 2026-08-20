package com.clinicare.service;

import com.clinicare.dto.DoctorPatientResponseDTO;
import com.clinicare.dto.DoctorResponseDTO;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only lookup of the doctors offered to patients in the booking form.
 * <p>
 * The returned {@link DoctorResponseDTO#id()} is the real {@code doctor_profiles.id}
 * so the frontend can submit it as {@code doctorId} when creating an
 * appointment. Names are assembled from each doctor's linked user account.
 */
@Service
public class DoctorService {

    private static final Comparator<DoctorProfile> BY_NAME = Comparator
            .comparing((DoctorProfile d) -> d.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(d -> d.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER);

    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public DoctorService(DoctorProfileRepository doctorProfileRepository,
                         AppointmentRepository appointmentRepository,
                         UserRepository userRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    /** Returns all registered doctors, ordered by last name. */
    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> listDoctors() {
        return doctorProfileRepository.findAll().stream()
                .sorted(BY_NAME)
                .map(d -> new DoctorResponseDTO(
                        d.getId(),
                        d.getUser().getFirstName() + " " + d.getUser().getLastName(),
                        d.getSpecialty()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorPatientResponseDTO> getMyPatients() {
        DoctorProfile doctor = requireCurrentDoctor();
        List<AppointmentStatus> relationshipStatuses =
                List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);
        return appointmentRepository.findByDoctorWithPatients(doctor, relationshipStatuses).stream()
                .map(Appointment::getPatient)
                .distinct()
                .map(p -> new DoctorPatientResponseDTO(
                        p.getId(),
                        fullName(p.getUser()),
                        p.getUser().getEmail(),
                        p.getDateOfBirth(),
                        p.getPhoneNumber(),
                        p.getUser().getCreatedAt()))
                .toList();
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    private DoctorProfile requireCurrentDoctor() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.DOCTOR) {
            throw new BadRequestException("Only doctors can perform this operation");
        }
        return doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}