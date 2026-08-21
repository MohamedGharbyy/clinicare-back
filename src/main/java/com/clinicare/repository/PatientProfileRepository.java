package com.clinicare.repository;

import com.clinicare.entity.PatientProfile;
import com.clinicare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    Optional<PatientProfile> findByUser(User user);

    Optional<PatientProfile> findByUserId(Long userId);
}