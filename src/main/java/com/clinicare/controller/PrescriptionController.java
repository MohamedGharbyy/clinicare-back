package com.clinicare.controller;

import com.clinicare.dto.PrescriptionRequestDTO;
import com.clinicare.dto.PrescriptionResponseDTO;
import com.clinicare.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for managing prescriptions.
 * <p>
 * Doctor-facing operations derive the acting doctor exclusively from the
 * authenticated principal (JWT). Patient-facing operations derive the
 * patient the same way. All business logic (role validation, relationship
 * checks, data mapping) is delegated to {@link PrescriptionService}; this
 * controller contains no business rules of its own.
 */
@RestController
@RequestMapping("/api")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    /**
     * Creates a new prescription for the authenticated doctor.
     * <p>
     * The doctor is always derived from the JWT principal. The referenced
     * patient must have an existing appointment relationship with the doctor.
     * The prescription must contain at least one medication.
     *
     * @param request the prescription request (patientId and medications list)
     * @return the created prescription, with HTTP 201 Created
     */
    @PostMapping("/doctor/prescriptions")
    public ResponseEntity<PrescriptionResponseDTO> createPrescription(
            @Valid @RequestBody PrescriptionRequestDTO request) {
        PrescriptionResponseDTO response = prescriptionService.createPrescription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns all prescriptions created by the authenticated doctor,
     * ordered chronologically by creation date.
     *
     * @return list of the doctor's prescriptions (empty if none exist)
     */
    @GetMapping("/doctor/prescriptions")
    public ResponseEntity<List<PrescriptionResponseDTO>> getMyCreatedPrescriptions() {
        List<PrescriptionResponseDTO> prescriptions = prescriptionService.getMyCreatedPrescriptions();
        return ResponseEntity.ok(prescriptions);
    }

    /**
     * Returns all prescriptions for the specified patient that were created
     * by the authenticated doctor, ordered chronologically by creation date.
     * <p>
     * The doctor must have an existing appointment relationship with the
     * patient to view their prescriptions.
     *
     * @param patientId the id of the patient whose prescriptions to retrieve
     * @return list of the patient's prescriptions for this doctor (empty if none exist)
     */
    @GetMapping("/doctor/prescriptions/patient/{patientId}")
    public ResponseEntity<List<PrescriptionResponseDTO>> getPatientPrescriptions(
            @PathVariable("patientId") Long patientId) {
        List<PrescriptionResponseDTO> prescriptions = prescriptionService.getPatientPrescriptions(patientId);
        return ResponseEntity.ok(prescriptions);
    }

    /**
     * Returns all prescriptions belonging to the authenticated patient,
     * ordered chronologically by creation date.
     *
     * @return list of the patient's prescriptions (empty if none exist)
     */
    @GetMapping("/patient/prescriptions")
    public ResponseEntity<List<PrescriptionResponseDTO>> getMyPrescriptions() {
        List<PrescriptionResponseDTO> prescriptions = prescriptionService.getMyPrescriptions();
        return ResponseEntity.ok(prescriptions);
    }
}
