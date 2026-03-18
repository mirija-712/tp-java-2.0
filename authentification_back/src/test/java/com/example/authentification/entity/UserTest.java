package com.example.authentification.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test léger pour couvrir les getters/setters principaux de l'entité User.
 */
class UserTest {

    /**
     * Teste que le constructeur avec email et passwordHash initialise correctement les champs.
     */
    @Test
    void constructeur_email_et_passwordHash_initialisent_les_champs() {
        User user = new User("user@example.com", "hash");
        assertEquals("user@example.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        // Par défaut, pas de lock ni d'échecs
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockUntil());
    }

    /**
     * Teste que le constructeur vide initialise les champs par défaut.
     */
    @Test
    void constructeur_vide_initialise_champs_defaut() {
        User user = new User();
        assertNull(user.getId());
        assertNull(user.getEmail());
        assertNull(user.getPasswordHash());
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockUntil());
        assertNull(user.getCreatedAt());
    }

    /**
     * Teste que tous les setters fonctionnent correctement.
     */
    @Test
    void setters_fonctionnent() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setFailedAttempts(3);
        LocalDateTime now = LocalDateTime.now();
        user.setLockUntil(now);
        user.setCreatedAt(now);

        assertEquals(1L, user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        assertEquals(3, user.getFailedAttempts());
        assertEquals(now, user.getLockUntil());
        assertEquals(now, user.getCreatedAt());
    }
}
