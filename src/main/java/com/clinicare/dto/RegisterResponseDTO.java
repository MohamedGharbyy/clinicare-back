package com.clinicare.dto;

import com.clinicare.entity.Role;

/**
 * Response returned after a successful registration. Never exposes the
 * password or its hash. {@code emailVerified} tells the client whether the
 * account still needs to confirm its email before it can log in.
 */
public record RegisterResponseDTO(Long id, String email, Role role, boolean emailVerified) {
}