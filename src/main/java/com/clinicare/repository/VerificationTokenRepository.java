package com.clinicare.repository;

import com.clinicare.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    void deleteByUser_IdAndUsedAtIsNull(Long userId);
}
