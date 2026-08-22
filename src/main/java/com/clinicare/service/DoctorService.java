package com.clinicare.service;

import com.clinicare.dto.ChangePasswordRequestDTO;
import com.clinicare.dto.DoctorPatientResponseDTO;
import com.clinicare.dto.DoctorProfileResponseDTO;
import com.clinicare.dto.DoctorResponseDTO;
import com.clinicare.dto.UpdateDoctorProfileRequestDTO;
import com.clinicare.dto.UpdateDoctorProfileResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Doctor account management and lookups: profile read/update, password change,
 * and the read-only list of doctors offered to patients in the booking form.
 * <p>
 * Like the other services, the acting doctor is derived exclusively from the JWT
 * principal — no doctor identifier is ever accepted from the client. Password
 * hashing reuses the application's {@link PasswordEncoder}, and token issuance
 * (when the login email changes) reuses the {@link JwtService}.
 */
@Service
public class DoctorService {

    private static final Comparator<DoctorProfile> BY_NAME = Comparator
            .comparing((DoctorProfile d) -> d.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(d -> d.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER);

    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public DoctorService(DoctorProfileRepository doctorProfileRepository,
                          AppointmentRepository appointmentRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Returns all registered doctors with an active account, ordered by last name. */
    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> listDoctors() {
        return doctorProfileRepository.findAll().stream()
                .filter(d -> d.getUser().getStatus() == AccountStatus.ACTIVE)
                .sorted(BY_NAME)
                .map(d -> new DoctorResponseDTO(
                        d.getId(),
                        d.getUser().getFirstName() + " " + d.getUser().getLastName(),
                        d.getSpecialty()))
                .toList();
    }

    /** Returns the authenticated doctor's current profile. */
    @Transactional(readOnly = true)
    public DoctorProfileResponseDTO getProfile() {
        User user = requireCurrentDoctorUser();
        DoctorProfile profile = doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
        return toResponse(user, profile);
    }

    /**
     * Updates the doctor's personal and professional information. When the login
     * email changes, a new JWT is issued so the client can refresh its session
     * transparently.
     */
    @Transactional
    public UpdateDoctorProfileResponseDTO updateProfile(UpdateDoctorProfileRequestDTO request) {
        User user = requireCurrentDoctorUser();

        String newEmail = request.email().trim();
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(newEmail);
        if (emailChanged && userRepository.existsByEmailAndStatusNot(newEmail, AccountStatus.DELETED)) {
            throw new BadRequestException("This email is already in use by another account");
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(newEmail);

        DoctorProfile profile = doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
        String phone = request.phoneNumber() == null ? null : request.phoneNumber().trim();
        profile.setPhoneNumber(phone.isEmpty() ? null : phone);
        String specialty = request.specialty() == null ? null : request.specialty().trim();
        profile.setSpecialty(specialty.isEmpty() ? null : specialty);
        String licenseNumber = request.licenseNumber() == null ? null : request.licenseNumber().trim();
        profile.setLicenseNumber(licenseNumber.isEmpty() ? null : licenseNumber);

        userRepository.save(user);
        doctorProfileRepository.save(profile);

        String token = emailChanged ? jwtService.generateToken(user) : null;
        return new UpdateDoctorProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                profile.getPhoneNumber(),
                profile.getSpecialty(),
                profile.getLicenseNumber(),
                user.getRole().name(),
                user.getCreatedAt().toString(),
                token);
    }

    /**
     * Changes the doctor's password after verifying the current one. The new
     * password must differ from the current and match its confirmation.
     */
    @Transactional
    public void changePassword(ChangePasswordRequestDTO request) {
        User user = requireCurrentDoctorUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from your current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<DoctorPatientResponseDTO> getMyPatients() {
        DoctorProfile doctor = requireCurrentDoctor();
        List<AppointmentStatus> relationshipStatuses =
                List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);
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
        return userRepository.findByEmailAndStatusNot(email, AccountStatus.DELETED)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    private DoctorProfile requireCurrentDoctor() {
        User user = requireCurrentDoctorUser();
        return doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Doctor profile not found"));
    }

    /** Resolves the authenticated user, requiring the {@link Role#DOCTOR} role. */
    private User requireCurrentDoctorUser() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.DOCTOR) {
            throw new BadRequestException("Only doctors can perform this operation");
        }
        return user;
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private DoctorProfileResponseDTO toResponse(User user, DoctorProfile profile) {
        return new DoctorProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                profile.getPhoneNumber(),
                profile.getSpecialty(),
                profile.getLicenseNumber(),
                user.getRole().name(),
                user.getCreatedAt().toString());
    }
}