package com.clinicare.controller;

import com.clinicare.dto.AdminDashboardResponseDTO;
import com.clinicare.dto.AdminDoctorResponseDTO;
import com.clinicare.dto.AdminPatientResponseDTO;
import com.clinicare.dto.AdminUserResponseDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.dto.BanRequestDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.exception.BadRequestException;
import com.clinicare.security.JwtService;
import com.clinicare.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final JwtService jwtService;

    public AdminController(AdminService adminService, JwtService jwtService) {
        this.adminService = adminService;
        this.jwtService = jwtService;
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

    /**
     * Returns every managed PATIENT/DOCTOR account. Deleted accounts are
     * excluded unless {@code includeDeleted=true}.
     */
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponseDTO>> listAccounts(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ResponseEntity.ok(adminService.listAccounts(includeDeleted));
    }

    /**
     * Soft-deletes an account. The user can no longer log in and is hidden from
     * the active list, but historical records are preserved. Admin accounts and
     * the acting Admin's own account are protected by the backend.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable("id") Long id, HttpServletRequest request) {
        adminService.deleteAccount(id, currentAdminId(request));
        return ResponseEntity.noContent().build();
    }

    /** Disables an account so the user cannot log in until re-enabled. */
    @PostMapping("/users/{id}/disable")
    public ResponseEntity<AdminUserResponseDTO> disableAccount(
            @PathVariable("id") Long id, HttpServletRequest request) {
        return ResponseEntity.ok(adminService.disableAccount(id, currentAdminId(request)));
    }

    /** Re-enables a disabled account. */
    @PostMapping("/users/{id}/enable")
    public ResponseEntity<AdminUserResponseDTO> enableAccount(
            @PathVariable("id") Long id, HttpServletRequest request) {
        return ResponseEntity.ok(adminService.enableAccount(id, currentAdminId(request)));
    }

    /** Temporarily bans an account for the supplied number of days. */
    @PostMapping("/users/{id}/ban")
    public ResponseEntity<AdminUserResponseDTO> banAccount(
            @PathVariable("id") Long id,
            @RequestBody BanRequestDTO body,
            HttpServletRequest request) {
        return ResponseEntity.ok(adminService.banAccount(id, body.durationDays(), currentAdminId(request)));
    }

    private Long currentAdminId(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BadRequestException("Missing authentication token");
        }
        return jwtService.extractUserId(header.substring(7));
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
