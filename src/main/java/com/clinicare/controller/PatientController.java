package com.clinicare.controller;

import com.clinicare.dto.ChangePasswordRequestDTO;
import com.clinicare.dto.UpdatePatientProfileRequestDTO;
import com.clinicare.dto.UpdatePatientProfileResponseDTO;
import com.clinicare.dto.UserProfileResponseDTO;
import com.clinicare.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient account management: profile read/update and password change.
 * <p>
 * Every operation derives the acting patient from the authenticated JWT principal;
 * no patient identifier is accepted from the client. Role enforcement happens in
 * {@link PatientService}, keeping this controller free of business rules.
 */
@RestController
@RequestMapping("/api/patient")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /** Returns the authenticated patient's current profile. */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDTO> getProfile() {
        return ResponseEntity.ok(patientService.getProfile());
    }

    /** Updates the authenticated patient's personal information. */
    @PutMapping("/profile")
    public ResponseEntity<UpdatePatientProfileResponseDTO> updateProfile(
            @Valid @RequestBody UpdatePatientProfileRequestDTO request) {
        return ResponseEntity.ok(patientService.updateProfile(request));
    }

    /** Changes the authenticated patient's password. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        patientService.changePassword(request);
        return ResponseEntity.ok().build();
    }
}
