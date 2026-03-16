package com.example.authentification.service;

import com.example.authentification.dto.LoginRequest;
import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.entity.User;
import com.example.authentification.exception.AuthenticationFailedException;
import com.example.authentification.exception.InvalidInputException;
import com.example.authentification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}

