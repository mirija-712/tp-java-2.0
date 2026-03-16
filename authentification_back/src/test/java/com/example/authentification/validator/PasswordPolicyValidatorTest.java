package com.example.authentification.validator;

import com.example.authentification.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de la politique de mot de passe TP2.
 */
class PasswordPolicyValidatorTest {

    @Test
    void password_valide_ne_leve_pas_d_exception() {
        // 12+ car., maj, min, chiffre, spécial
        assertDoesNotThrow(() -> PasswordPolicyValidator.validate("Abcd1234!@#$"));
    }

    @Test
    void password_trop_court_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> PasswordPolicyValidator.validate("Abc1!")); // 5 caractères
    }

    @Test
    void password_sans_majuscule_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> PasswordPolicyValidator.validate("abcd1234!@#$"));
    }

    @Test
    void password_sans_minuscule_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> PasswordPolicyValidator.validate("ABCD1234!@#$"));
    }

    @Test
    void password_sans_chiffre_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> PasswordPolicyValidator.validate("Abcdefgh!@#$"));
    }

    @Test
    void password_sans_special_leve_InvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> PasswordPolicyValidator.validate("Abcd1234Abcd"));
    }
}

