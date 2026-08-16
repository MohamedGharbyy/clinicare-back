package com.clinicare.dto;

import com.clinicare.entity.Role;

/**
 * Response returned after a successful registration. Never exposes the
 * password or its hash.
 */
public record RegisterResponseDTO(Long id, String email, Role role) {
}