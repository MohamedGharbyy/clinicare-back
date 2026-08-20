package com.clinicare.repository;

import com.clinicare.entity.Appointment;
import com.clinicare.entity.AppointmentStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatient(PatientProfile patient);

    List<Appointment> findByDoctor(DoctorProfile doctor);

    List<Appointment> findByPatientAndStatus(PatientProfile patient, AppointmentStatus status);

    List<Appointment> findByDoctorAndStatus(DoctorProfile doctor, AppointmentStatus status);

    boolean existsByPatientAndDoctor(PatientProfile patient, DoctorProfile doctor);

    boolean existsByPatientAndDoctorAndStatusIn(PatientProfile patient, DoctorProfile doctor, java.util.Collection<AppointmentStatus> statuses);

    @Query("""
            SELECT DISTINCT a
            FROM Appointment a
            JOIN FETCH a.patient p
            JOIN FETCH p.user
            WHERE a.doctor = :doctor
              AND a.status IN :statuses
            """)
    List<Appointment> findByDoctorWithPatients(@Param("doctor") DoctorProfile doctor,
                                               @Param("statuses") List<AppointmentStatus> statuses);
}
