package com.example.authentification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de démarrage minimal de l'application Spring Boot.
 * <p>
 * Objectif : vérifier que le contexte Spring se crée correctement avec le profil {@code test}
 * (configuration H2, beans, contrôleurs, services, repositories, etc.).
 * Si ce test échoue, c'est généralement qu'il y a un problème de configuration globale.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthentificationApplicationTests {

    /**
     * Vérifie simplement que le contexte Spring Boot démarre sans exception.
     * Aucune assertion nécessaire : si le démarrage échoue, le test sera marqué en erreur.
     */
    @Test
    void contextLoads() {}

}