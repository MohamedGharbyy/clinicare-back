package com.clinicare.repository;

import com.clinicare.entity.PrescriptionMedication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionMedicationRepository extends JpaRepository<PrescriptionMedication, Long> {
}
