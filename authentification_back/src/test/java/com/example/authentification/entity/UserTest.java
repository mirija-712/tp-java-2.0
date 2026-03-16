package com.example.authentification.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test léger pour couvrir les getters/setters principaux de l'entité User.
 */
class UserTest {

    @Test
    void constructeur_email_et_passwordHash_initialisent_les_champs() {
        User user = new User("user@example.com", "hash");
        assertEquals("user@example.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        // Par défaut, pas de lock ni d'échecs
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockUntil());
    }
}

