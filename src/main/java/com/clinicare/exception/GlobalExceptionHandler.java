package com.clinicare.exception;

import com.clinicare.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions thrown by controllers into clean, uniform JSON error
 * responses instead of leaking stack traces to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation failed", fields));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(HttpStatus.CONFLICT.value(),
                        "Email is already registered", Map.of("email", ex.getEmail())));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(AccountDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AccountDeletedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDeleted(AccountDeletedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AccountBannedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountBanned(AccountBannedException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (ex.getBanExpiresAt() != null) {
            fields.put("banExpiresAt", ex.getBanExpiresAt().toString());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), fields));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(),
                        "Invalid credentials", Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(),
                        "Malformed request body", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error", Map.of()));
    }
}