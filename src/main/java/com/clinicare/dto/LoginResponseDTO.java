package com.clinicare.dto;

import com.clinicare.entity.Role;

/**
 * Response returned after a successful login: a JWT plus basic user info.
 */
public record LoginResponseDTO(String token, Long id, String email, Role role) {
}