package com.clinicare.dto;

import java.util.Map;

/**
 * Uniform JSON error body produced by the global exception handler.
 */
public record ApiErrorResponse(int status, String message, Map<String, String> fields) {
}