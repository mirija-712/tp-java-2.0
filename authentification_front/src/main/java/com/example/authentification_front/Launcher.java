package com.example.authentification_front;

import javafx.application.Application;

/**
 * Classe de lancement dédiée pour les environnements où JavaFX
 * ne supporte pas directement une classe main sur {@link HelloApplication}.
 * <p>
 * Délègue simplement au {@code Application.launch(...)} standard.
 * </p>
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(HelloApplication.class, args);
    }
}
