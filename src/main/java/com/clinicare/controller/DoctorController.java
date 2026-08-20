package com.clinicare.controller;

import com.clinicare.dto.DoctorResponseDTO;
import com.clinicare.service.DoctorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * Read-only list of doctors available for booking.
 * <p>
 * Continuous with the rest of the API: the endpoint requires a valid bearer
 * token (see {@link com.clinicare.config.SecurityConfig}), and every id returned
 * corresponds to a real {@code doctor_profiles} row.
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ResponseEntity<List<DoctorResponseDTO>> listDoctors() {
        return ResponseEntity.ok(doctorService.listDoctors());
    }

    @GetMapping("/patients")
    public RedirectView redirectPatients() {
        return new RedirectView("/api/doctor/patients");
    }
}