package com.clinicare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only endpoints. All routes under {@code /api/admin/**} are protected
 * by Spring Security and require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Admin dashboard info endpoint. Returns basic admin dashboard data.
     * Protected by Spring Security: only users with ADMIN role can access.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to admin dashboard",
                "status", "success"
        ));
    }
}
