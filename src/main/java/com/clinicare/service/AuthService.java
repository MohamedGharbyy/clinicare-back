package com.clinicare.service;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.dto.ResendVerificationResponseDTO;
import com.clinicare.dto.LoginRequestDTO;
import com.clinicare.dto.LoginResponseDTO;
import com.clinicare.dto.RegisterRequestDTO;
import com.clinicare.dto.RegisterResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.exception.AccountBannedException;
import com.clinicare.exception.AccountDeletedException;
import com.clinicare.exception.AccountDisabledException;
import com.clinicare.exception.BadRequestException;
import com.clinicare.exception.EmailAlreadyExistsException;
import com.clinicare.exception.EmailNotVerifiedException;
import com.clinicare.exception.InvalidCredentialsException;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.PatientProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       PatientProfileRepository patientProfileRepository,
                       DoctorProfileRepository doctorProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Role ADMIN cannot be registered via this endpoint");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setEmailVerified(false);
        userRepository.save(user);

        if (request.role() == Role.DOCTOR) {
            DoctorProfile profile = new DoctorProfile();
            profile.setUser(user);
            profile.setSpecialty(request.specialty());
            profile.setLicenseNumber(request.licenseNumber());
            profile.setPhoneNumber(request.phoneNumber());
            doctorProfileRepository.save(profile);
        } else {
            PatientProfile profile = new PatientProfile();
            profile.setUser(user);
            profile.setDateOfBirth(request.dateOfBirth());
            profile.setPhoneNumber(request.phoneNumber());
            patientProfileRepository.save(profile);
        }

        emailVerificationService.createTokenAndSendEmail(user);

        return new RegisterResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEmailVerified());
    }

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.reconcileBan()) {
            userRepository.save(user);
        }

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        if (user.getStatus() == AccountStatus.DELETED) {
            throw new AccountDeletedException();
        }
        if (user.getStatus() == AccountStatus.BANNED) {
            throw new AccountBannedException(user.getBanExpiresAt());
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponseDTO(
                jwtService.generateToken(user),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole());
    }

    public EmailVerificationResponseDTO verifyEmail(String email, String code) {
        return emailVerificationService.verifyEmail(email, code);
    }

    public ResendVerificationResponseDTO resendVerification(String email) {
        return emailVerificationService.resendCode(email);
    }
}