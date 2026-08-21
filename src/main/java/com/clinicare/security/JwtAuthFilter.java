package com.clinicare.security;

import com.clinicare.entity.User;
import com.clinicare.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads an {@code Authorization: Bearer <token>} header on every request and,
 * when the token is valid, populates the {@link SecurityContextHolder} with an
 * authenticated principal carrying the user's email and {@code ROLE_*} authority.
 * <p>
 * The linked account's lifecycle state is enforced here as well: a disabled,
 * soft-deleted, or (not-yet-expired) banned account is treated as
 * unauthenticated, so the block takes effect immediately even for sessions
 * that were already signed in.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (jwtService.isTokenValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                if (user.reconcileBan()) {
                    userRepository.save(user);
                }
                if (!user.isEffectivelyBlocked()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}