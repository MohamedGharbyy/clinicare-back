package com.clinicare.repository;

import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndStatusNot(String email, AccountStatus status);

    boolean existsByEmailAndStatusNot(String email, AccountStatus status);

    List<User> findByRole(Role role);

    long countByRoleAndStatusNot(Role role, AccountStatus status);
}
