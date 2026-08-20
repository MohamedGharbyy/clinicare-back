package com.clinicare.repository;

import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByDoctor(DoctorProfile doctor);

    List<Prescription> findByPatient(PatientProfile patient);

    List<Prescription> findByDoctorAndPatient(DoctorProfile doctor, PatientProfile patient);
}
