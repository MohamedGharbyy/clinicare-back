package com.clinicare.dto;

/**
 * Request body for temporarily banning an account. {@code durationDays}
 * expresses how long the ban should last (e.g. 1, 7, or 30 days).
 */
public record BanRequestDTO(int durationDays) {
}
