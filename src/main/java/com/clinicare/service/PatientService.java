package com.clinicare.service;

import com.clinicare.dto.ChangePasswordRequestDTO;
import com.clinicare.dto.UpdatePatientProfileRequestDTO;
import com.clinicare.dto.UpdatePatientProfileResponseDTO;
import com.clinicare.dto.UserProfileResponseDTO;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.BadRequestException;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management for the authenticated patient: reading and updating personal
 * information, and changing the password.
 * <p>
 * Like the other services, the acting patient is derived exclusively from the JWT
 * principal — no patient identifier is ever accepted from the client. Password
 * hashing reuses the application's {@link PasswordEncoder}, and token issuance (when
 * the login email changes) reuses the {@link JwtService}.
 */
@Service
public class PatientService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public PatientService(UserRepository userRepository,
                          PatientProfileRepository patientProfileRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Returns the authenticated patient's current profile. */
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getProfile() {
        User user = requireCurrentPatientUser();
        PatientProfile profile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Patient profile not found"));
        return toResponse(user, profile);
    }

    /**
     * Updates the patient's personal information. When the login email changes, a
     * new JWT is issued so the client can refresh its session transparently.
     */
    @Transactional
    public UpdatePatientProfileResponseDTO updateProfile(UpdatePatientProfileRequestDTO request) {
        User user = requireCurrentPatientUser();

        String newEmail = request.email().trim();
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(newEmail);
        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            throw new BadRequestException("This email is already in use by another account");
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(newEmail);

        PatientProfile profile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Patient profile not found"));
        String phone = request.phoneNumber() == null ? null : request.phoneNumber().trim();
        profile.setPhoneNumber(phone.isEmpty() ? null : phone);

        userRepository.save(user);
        patientProfileRepository.save(profile);

        String token = emailChanged ? jwtService.generateToken(user) : null;
        return new UpdatePatientProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                profile.getPhoneNumber(),
                user.getRole().name(),
                user.getCreatedAt().toString(),
                token);
    }

    /**
     * Changes the patient's password after verifying the current one. The new
     * password must differ from the current and match its confirmation.
     */
    @Transactional
    public void changePassword(ChangePasswordRequestDTO request) {
        User user = requireCurrentPatientUser();

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

    /**
     * Resolves the {@link User} for the authenticated principal. The
     * {@link com.clinicare.security.JwtAuthFilter} stores the user's email as the
     * principal, so it is a {@code String} email.
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

    /** Resolves the authenticated user, requiring the {@link Role#PATIENT} role. */
    private User requireCurrentPatientUser() {
        User user = resolveCurrentUser();
        if (user.getRole() != Role.PATIENT) {
            throw new BadRequestException("Only patients can perform this operation");
        }
        return user;
    }

    private UserProfileResponseDTO toResponse(User user, PatientProfile profile) {
        return new UserProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                profile.getPhoneNumber(),
                user.getRole().name(),
                user.getCreatedAt().toString());
    }
}
