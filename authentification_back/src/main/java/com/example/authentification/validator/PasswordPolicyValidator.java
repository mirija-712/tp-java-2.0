package com.example.authentification.validator;

import com.example.authentification.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie que le mot de passe respecte la politique TP2 :
 * minimum 12 caractères, au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial.
 */
public final class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicyValidator() {
    }

    /**
     * Vérifie que le mot de passe respecte toutes les contraintes TP2.
     * <p>
     * Si une contrainte n'est pas respectée, lève une {@link InvalidInputException}
     * avec un message indiquant les règles manquantes.
     * </p>
     *
     * @param password mot de passe en clair à valider (tel que reçu dans RegisterRequest)
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidInputException("Le mot de passe doit contenir au minimum " + MIN_LENGTH + " caractères");
        }

        List<String> errors = new ArrayList<>();
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            errors.add("au moins une majuscule");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            errors.add("au moins une minuscule");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            errors.add("au moins un chiffre");
        }
        if (!password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            errors.add("au moins un caractère spécial");
        }

        if (!errors.isEmpty()) {
            throw new InvalidInputException("Le mot de passe doit contenir : " + String.join(", ", errors));
        }
    }
}

