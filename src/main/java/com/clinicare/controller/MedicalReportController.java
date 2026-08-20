package com.clinicare.controller;

import com.clinicare.dto.MedicalReportRequestDTO;
import com.clinicare.dto.MedicalReportResponseDTO;
import com.clinicare.service.MedicalReportService;
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

@RestController
@RequestMapping("/api")
public class MedicalReportController {

    private final MedicalReportService medicalReportService;

    public MedicalReportController(MedicalReportService medicalReportService) {
        this.medicalReportService = medicalReportService;
    }

    @PostMapping("/doctor/medical-reports")
    public ResponseEntity<MedicalReportResponseDTO> createMedicalReport(
            @Valid @RequestBody MedicalReportRequestDTO request) {
        MedicalReportResponseDTO response = medicalReportService.createMedicalReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/doctor/medical-reports")
    public ResponseEntity<List<MedicalReportResponseDTO>> getMyCreatedReports() {
        List<MedicalReportResponseDTO> reports = medicalReportService.getMyCreatedReports();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/doctor/medical-reports/patient/{patientId}")
    public ResponseEntity<List<MedicalReportResponseDTO>> getPatientReports(
            @PathVariable("patientId") Long patientId) {
        List<MedicalReportResponseDTO> reports = medicalReportService.getPatientReports(patientId);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/patient/medical-reports")
    public ResponseEntity<List<MedicalReportResponseDTO>> getMyReports() {
        List<MedicalReportResponseDTO> reports = medicalReportService.getMyReports();
        return ResponseEntity.ok(reports);
    }
}
