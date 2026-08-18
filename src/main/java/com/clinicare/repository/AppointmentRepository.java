package com.clinicare.repository;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatient(PatientProfile patient);

    List<Appointment> findByDoctor(DoctorProfile doctor);

    List<Appointment> findByPatientAndStatus(PatientProfile patient, AppointmentStatus status);

    List<Appointment> findByDoctorAndStatus(DoctorProfile doctor, AppointmentStatus status);
}
