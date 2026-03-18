package com.example.authentification.service;

import com.example.authentification.dto.LoginRequest;
import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.entity.User;
import com.example.authentification.exception.AccountLockedException;
import com.example.authentification.exception.AuthenticationFailedException;
import com.example.authentification.exception.InvalidInputException;
import com.example.authentification.exception.ResourceConflictException;
import com.example.authentification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires supplémentaires sur AuthService pour améliorer la couverture TP2.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_et_login_avec_motDePasseFort_fonctionnent() {
        String email = "flow@test.com";
        String password = "Abcd1234!@#$";

        User user = authService.register(new RegisterRequest(email, password));
        assertEquals(email, user.getEmail());

        User logged = authService.login(new LoginRequest(email, password));
        assertEquals(user.getId(), logged.getId());
    }

    @Test
    void login_avec_motDePasseVide_leve_InvalidInputException() {
        String email = "empty@test.com";
        String password = "Abcd1234!@#$";
        authService.register(new RegisterRequest(email, password));

        assertThrows(InvalidInputException.class,
                () -> authService.login(new LoginRequest(email, "")));
    }

    @Test
    void login_avec_mauvaisMotDePasse_leve_AuthenticationFailedException() {
        String email = "badpwd@test.com";
        String password = "Abcd1234!@#$";
        authService.register(new RegisterRequest(email, password));

        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(new LoginRequest(email, "Wrong123!")));
    }

    @Test
    void register_avec_emailInvalide_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.register(new RegisterRequest("invalid", "Abcd1234!@#$")));
    }

    @Test
    void register_avec_emailVide_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.register(new RegisterRequest("", "Abcd1234!@#$")));
    }

    @Test
    void register_avec_emailExistant_leve_ResourceConflictException() {
        String email = "duplicate@test.com";
        String password = "Abcd1234!@#$";
        authService.register(new RegisterRequest(email, password));

        assertThrows(ResourceConflictException.class,
                () -> authService.register(new RegisterRequest(email, "Different123!")));
    }

    @Test
    void login_avec_emailInvalide_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.login(new LoginRequest("invalid", "Abcd1234!@#$")));
    }

    @Test
    void login_avec_emailVide_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.login(new LoginRequest("", "Abcd1234!@#$")));
    }

    @Test
    void login_avec_emailInconnu_leve_AuthenticationFailedException() {
        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(new LoginRequest("unknown@test.com", "Abcd1234!@#$")));
    }

    @Test
    void register_avec_emailNull_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.register(new RegisterRequest(null, "Abcd1234!@#$")));
    }

    @Test
    void login_avec_emailNull_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> authService.login(new LoginRequest(null, "Abcd1234!@#$")));
    }

    @Test
    void login_avec_compte_verrouille_leve_AccountLockedException() {
        String email = "locked@test.com";
        String password = "Abcd1234!@#$";
        User user = authService.register(new RegisterRequest(email, password));

        // Simuler un verrouillage en plaçant lockUntil dans le futur
        user.setLockUntil(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        assertThrows(AccountLockedException.class,
                () -> authService.login(new LoginRequest(email, password)));
    }

    @Test
    void login_apres_periode_deverrouillage_reinitialise_les_counters() {
        String email = "unlock@test.com";
        String password = "Abcd1234!@#$";
        User user = authService.register(new RegisterRequest(email, password));

        // Simuler un verrouillage expiré
        user.setFailedAttempts(3);
        user.setLockUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        User logged = authService.login(new LoginRequest(email, password));
        assertEquals(user.getId(), logged.getId());

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, refreshed.getFailedAttempts());
        assertEquals(null, refreshed.getLockUntil());
    }

    @Test
    void login_avec_5_echecs_verrouille_le_compte() {
        String email = "brute@test.com";
        String password = "Abcd1234!@#$";
        authService.register(new RegisterRequest(email, password));

        for (int i = 0; i < 4; i++) {
            assertThrows(AuthenticationFailedException.class,
                    () -> authService.login(new LoginRequest(email, "Wrong123!")));
        }

        assertThrows(AccountLockedException.class,
                () -> authService.login(new LoginRequest(email, "Wrong123!")));

        User user = userRepository.findByEmail(email).orElseThrow();
        // Le compte doit être verrouillé et avoir au moins 5 tentatives
        assertEquals(5, user.getFailedAttempts());
        assertEquals(true, user.getLockUntil() != null);
    }
}
