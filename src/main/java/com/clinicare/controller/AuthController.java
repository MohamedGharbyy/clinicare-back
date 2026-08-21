package com.clinicare.controller;

import com.clinicare.dto.EmailVerificationResponseDTO;
import com.clinicare.dto.LoginRequestDTO;
import com.clinicare.dto.LoginResponseDTO;
import com.clinicare.dto.RegisterRequestDTO;
import com.clinicare.dto.RegisterResponseDTO;
import com.clinicare.dto.ResendVerificationRequestDTO;
import com.clinicare.dto.ResendVerificationResponseDTO;
import com.clinicare.dto.VerifyEmailRequestDTO;
import com.clinicare.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerificationResponseDTO> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request) {
        return ResponseEntity.ok(authService.verifyEmail(request.email(), request.code()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ResendVerificationResponseDTO> resendVerification(
            @Valid @RequestBody ResendVerificationRequestDTO request) {
        return ResponseEntity.ok(authService.resendVerification(request.email()));
    }
}
