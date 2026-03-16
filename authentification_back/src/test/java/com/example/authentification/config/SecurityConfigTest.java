package com.example.authentification.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test simple pour couvrir SecurityConfig / PasswordEncoder (BCrypt).
 */
class SecurityConfigTest {

    @Test
    void passwordEncoder_encodeEtMatches() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();

        String raw = "Abcd1234!@#$";
        String hash = encoder.encode(raw);

        assertTrue(encoder.matches(raw, hash));
    }
}

