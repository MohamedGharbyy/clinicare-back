package com.clinicare.repository;

import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.MedicalReport;
import com.clinicare.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalReportRepository extends JpaRepository<MedicalReport, Long> {

    List<MedicalReport> findByDoctor(DoctorProfile doctor);

    List<MedicalReport> findByPatient(PatientProfile patient);

    List<MedicalReport> findByDoctorAndPatient(DoctorProfile doctor, PatientProfile patient);
}
