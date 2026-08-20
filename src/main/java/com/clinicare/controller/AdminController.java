package com.clinicare.controller;

import com.clinicare.dto.AdminDashboardResponseDTO;
import com.clinicare.dto.AdminDoctorResponseDTO;
import com.clinicare.dto.AdminPatientResponseDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only endpoints. All routes under {@code /api/admin/**} are protected
 * by Spring Security and require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Admin dashboard info endpoint. Returns the live summary counters
     * (total patients, total doctors, total appointments) computed from the
     * real repositories. Protected by Spring Security: only users with the
     * ADMIN role can access.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponseDTO> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    /** Returns every registered patient, ordered by name. */
    @GetMapping("/patients")
    public ResponseEntity<List<AdminPatientResponseDTO>> listPatients() {
        return ResponseEntity.ok(adminService.listPatients());
    }

    /** Returns every registered doctor, ordered by name. */
    @GetMapping("/doctors")
    public ResponseEntity<List<AdminDoctorResponseDTO>> listDoctors() {
        return ResponseEntity.ok(adminService.listDoctors());
    }

    /** Returns every appointment across the platform, ordered by date/time. */
    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponseDTO>> listAppointments() {
        return ResponseEntity.ok(adminService.listAppointments());
    }

    /** Returns every prescription across the platform, ordered by creation date. */
    @GetMapping("/prescriptions")
    public ResponseEntity<List<PrescriptionResponseDTO>> listPrescriptions() {
        return ResponseEntity.ok(adminService.listAllPrescriptions());
    }

    /**
     * Cancels an appointment on behalf of the clinic.
     * Protected by Spring Security: only users with ADMIN role can access.
     */
    @DeleteMapping("/appointments/{id}")
    public ResponseEntity<AppointmentResponseDTO> cancelAppointment(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.cancelAppointment(id));
    }
}
