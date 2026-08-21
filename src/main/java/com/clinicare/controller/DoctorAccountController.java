package com.clinicare.controller;

import com.clinicare.dto.ChangePasswordRequestDTO;
import com.clinicare.dto.DoctorProfileResponseDTO;
import com.clinicare.dto.UpdateDoctorProfileRequestDTO;
import com.clinicare.dto.UpdateDoctorProfileResponseDTO;
import com.clinicare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Doctor account management: profile read/update and password change.
 * <p>
 * Every operation derives the acting doctor from the authenticated JWT principal;
 * no doctor identifier is accepted from the client. Role enforcement happens in
 * {@link DoctorService}, keeping this controller free of business rules.
 */
@RestController
@RequestMapping("/api/doctor")
public class DoctorAccountController {

    private final DoctorService doctorService;

    public DoctorAccountController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /** Returns the authenticated doctor's current profile. */
    @GetMapping("/profile")
    public ResponseEntity<DoctorProfileResponseDTO> getProfile() {
        return ResponseEntity.ok(doctorService.getProfile());
    }

    /** Updates the authenticated doctor's personal and professional information. */
    @PutMapping("/profile")
    public ResponseEntity<UpdateDoctorProfileResponseDTO> updateProfile(
            @Valid @RequestBody UpdateDoctorProfileRequestDTO request) {
        return ResponseEntity.ok(doctorService.updateProfile(request));
    }

    /** Changes the authenticated doctor's password. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        doctorService.changePassword(request);
        return ResponseEntity.ok().build();
    }
}
