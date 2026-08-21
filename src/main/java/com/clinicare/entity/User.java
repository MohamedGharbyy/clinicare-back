package com.clinicare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Application user account. Password is stored only as a hash.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "ban_expires_at")
    private LocalDateTime banExpiresAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public LocalDateTime getBanExpiresAt() {
        return banExpiresAt;
    }

    public void setBanExpiresAt(LocalDateTime banExpiresAt) {
        this.banExpiresAt = banExpiresAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedById() {
        return deletedById;
    }

    public void setDeletedById(Long deletedById) {
        this.deletedById = deletedById;
    }

    /** Whether this account has been soft-deleted by an Admin. */
    public boolean isDeleted() {
        return status == AccountStatus.DELETED;
    }

    /** Whether an Admin has explicitly disabled this account. */
    public boolean isDisabled() {
        return status == AccountStatus.DISABLED;
    }

    /** Whether this account is currently under a temporary ban. */
    public boolean isBanned() {
        return status == AccountStatus.BANNED;
    }

    /**
     * Whether the account is blocked right now. A disabled or soft-deleted
     * account is always blocked. A banned account is blocked until
     * {@code banExpiresAt} passes.
     */
    public boolean isEffectivelyBlocked() {
        if (status == AccountStatus.DISABLED || status == AccountStatus.DELETED) {
            return true;
        }
        if (status == AccountStatus.BANNED) {
            return banExpiresAt == null || banExpiresAt.isAfter(LocalDateTime.now());
        }
        return false;
    }

    /**
     * Lifts an expired ban, returning the account to {@code ACTIVE}. Does
     * nothing when the account is not banned or the ban has not yet expired.
     *
     * @return {@code true} when the account state was changed
     */
    public boolean reconcileBan() {
        if (status == AccountStatus.BANNED
                && banExpiresAt != null
                && banExpiresAt.isBefore(LocalDateTime.now())) {
            status = AccountStatus.ACTIVE;
            banExpiresAt = null;
            return true;
        }
        return false;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}