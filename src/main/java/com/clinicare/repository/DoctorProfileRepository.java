package com.clinicare.repository;

import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {

    Optional<DoctorProfile> findByUser(User user);
}