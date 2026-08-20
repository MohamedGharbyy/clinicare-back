package com.clinicare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a prescription issued by a doctor for a patient.
 * <p>
 * The doctor and patient are referenced through their existing
 * {@link DoctorProfile} and {@link PatientProfile} entities, and the
 * {@link #creationDate} is populated automatically by JPA auditing.
 */
@Entity
@Table(name = "prescriptions")
@EntityListeners(AuditingEntityListener.class)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @CreatedDate
    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @OneToMany(mappedBy = "prescription", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionMedication> medications = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DoctorProfile getDoctor() {
        return doctor;
    }

    public void setDoctor(DoctorProfile doctor) {
        this.doctor = doctor;
    }

    public PatientProfile getPatient() {
        return patient;
    }

    public void setPatient(PatientProfile patient) {
        this.patient = patient;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public List<PrescriptionMedication> getMedications() {
        return medications;
    }

    public void setMedications(List<PrescriptionMedication> medications) {
        this.medications = medications;
    }
}
