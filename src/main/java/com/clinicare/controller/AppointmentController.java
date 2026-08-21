package com.clinicare.controller;

import com.clinicare.dto.AppointmentRequestDTO;
import com.clinicare.dto.AppointmentResponseDTO;
import com.clinicare.dto.DoctorPatientResponseDTO;
import com.clinicare.service.AppointmentService;
import com.clinicare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for managing patient-doctor appointments.
 * <p>
 * Patient-facing operations derive the acting patient exclusively from the
 * authenticated principal (JWT). No patient identifier is ever accepted from
 * the client — the {@link AppointmentRequestDTO} intentionally omits a
 * patient id, and ownership is resolved and enforced in the service layer.
 * Doctor-facing operations derive the acting doctor the same way.
 * <p>
 * All business logic (role validation, ownership checks, status transitions)
 * is delegated to {@link AppointmentService}; this controller contains no
 * business rules of its own.
 */
@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final DoctorService doctorService;

    public AppointmentController(AppointmentService appointmentService, DoctorService doctorService) {
        this.appointmentService = appointmentService;
        this.doctorService = doctorService;
    }

    /**
     * Creates a new appointment request for the authenticated patient.
     * <p>
     * The patient is always derived from the JWT principal; any patient id
     * present in the request body is ignored by the service layer. New
     * appointments are persisted with a {@code PENDING} status.
     *
     * @param request the appointment request (doctorId, date, time, reason, optional notes)
     * @return the created appointment, with HTTP 201 Created
     */
    @PostMapping("/patient/appointments")
    public ResponseEntity<AppointmentResponseDTO> createAppointment(
            @Valid @RequestBody AppointmentRequestDTO request) {
        AppointmentResponseDTO response = appointmentService.createAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns all appointments belonging to the authenticated patient,
     * ordered chronologically by date/time.
     *
     * @return list of the patient's appointments (empty if none exist)
     */
    @GetMapping("/patient/appointments")
    public ResponseEntity<List<AppointmentResponseDTO>> getMyAppointments() {
        List<AppointmentResponseDTO> appointments = appointmentService.getMyAppointments();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Returns upcoming appointments — those whose scheduled date/time is
     * strictly in the future — for the authenticated patient, ordered
     * chronologically by date/time.
     *
     * @return list of upcoming appointments (empty if none exist)
     */
    @GetMapping("/patient/appointments/upcoming")
    public ResponseEntity<List<AppointmentResponseDTO>> getUpcomingAppointments() {
        List<AppointmentResponseDTO> appointments = appointmentService.getUpcomingAppointments();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Cancels one of the authenticated patient's own appointments.
     * <p>
     * Only the owning patient may cancel, and only when the current status
     * allows it ({@code PENDING} or {@code CONFIRMED}). The appointment is
     * then marked {@code CANCELLED} and the updated representation is returned.
     *
     * @param appointmentId the id of the appointment to cancel
     * @return the cancelled appointment with HTTP 200 OK
     */
    @DeleteMapping("/patient/appointments/{id}")
    public ResponseEntity<AppointmentResponseDTO> cancelAppointment(
            @PathVariable("id") Long appointmentId,
            @RequestParam(name = "reason", required = false) String reason) {
        AppointmentResponseDTO response = appointmentService.cancelAppointment(appointmentId, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns all patients the authenticated doctor has an established
     * appointment relationship with (CONFIRMED or COMPLETED), ordered
     * by name.
     *
     * @return list of the doctor's patients (empty if none exist)
     */
    @GetMapping("/doctor/patients")
    public ResponseEntity<List<DoctorPatientResponseDTO>> getMyPatients() {
        List<DoctorPatientResponseDTO> patients = doctorService.getMyPatients();
        return ResponseEntity.ok(patients);
    }

    /**
     * Returns all appointments assigned to the authenticated doctor,
     * ordered chronologically by date/time.
     *
     * @return list of the doctor's appointments (empty if none exist)
     */
    @GetMapping("/doctor/appointments")
    public ResponseEntity<List<AppointmentResponseDTO>> getDoctorAppointments() {
        List<AppointmentResponseDTO> appointments = appointmentService.getDoctorAppointments();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Accepts one of the authenticated doctor's pending appointment requests.
     * <p>
     * Only the owning doctor may accept, and only while the request is still
     * {@code PENDING}. The appointment is then marked {@code CONFIRMED} (the
     * system's accepted state) and the updated representation is returned.
     *
     * @param appointmentId the id of the appointment to accept
     * @return the accepted appointment with HTTP 200 OK
     */
    @PostMapping("/doctor/appointments/{id}/accept")
    public ResponseEntity<AppointmentResponseDTO> acceptAppointment(
            @PathVariable("id") Long appointmentId) {
        AppointmentResponseDTO response = appointmentService.acceptAppointment(appointmentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Rejects one of the authenticated doctor's pending appointment requests.
     * <p>
     * Only the owning doctor may reject, and only while the request is still
     * {@code PENDING}. The appointment is then marked {@code REJECTED} and the
     * updated representation is returned.
     *
     * @param appointmentId the id of the appointment to reject
     * @return the rejected appointment with HTTP 200 OK
     */
    @PostMapping("/doctor/appointments/{id}/reject")
    public ResponseEntity<AppointmentResponseDTO> rejectAppointment(
            @PathVariable("id") Long appointmentId) {
        AppointmentResponseDTO response = appointmentService.rejectAppointment(appointmentId);
        return ResponseEntity.ok(response);
    }
}
