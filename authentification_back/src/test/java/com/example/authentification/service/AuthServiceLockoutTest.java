package com.example.authentification.service;

import com.example.authentification.dto.LoginRequest;
import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.entity.User;
import com.example.authentification.exception.AccountLockedException;
import com.example.authentification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests ciblés sur le verrouillage / déverrouillage de compte (TP2).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceLockoutTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
    }

    @Test
    void lockout_apres_cinq_echecs_puis_deverrouillage_apres_expiration() {
        // Inscription avec un mot de passe conforme TP2
        String email = "lock-reset@test.com";
        String password = "Abcd1234!@#$";
        authService.register(new RegisterRequest(email, password));

        // 5 tentatives échouées pour verrouiller le compte
        for (int i = 0; i < 5; i++) {
            String wrong = "Wrong123!";
            assertThrows(RuntimeException.class,
                    () -> authService.login(new LoginRequest(email, wrong)));
        }

        User user = userRepository.findByEmail(email).orElseThrow();
        // Vérifier qu'on est bien verrouillé maintenant
        assertThrows(AccountLockedException.class,
                () -> authService.login(new LoginRequest(email, password)));

        // Simuler l'expiration du lock : placer lockUntil dans le passé
        user.setLockUntil(LocalDateTime.now().minusMinutes(5));
        user.setFailedAttempts(0);
        userRepository.save(user);

        // Après expiration, un login correct doit de nouveau réussir
        assertDoesNotThrow(() -> authService.login(new LoginRequest(email, password)));
    }
}

