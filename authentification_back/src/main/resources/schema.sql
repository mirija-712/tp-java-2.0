-- =============================================================================
-- Schema SQL : créé au démarrage de l'application (spring.sql.init.mode=always)
-- Objectif : créer la table users si elle n'existe pas
-- =============================================================================

-- id : clé primaire auto-incrémentée
-- email : unique (pas de doublon), obligatoire
-- password_hash : hash du mot de passe (BCrypt - TP2)
-- failed_attempts : compteur d'échecs de login consécutifs
-- lock_until : date/heure jusqu'à laquelle le compte est verrouillé
-- created_at : date de création (rempli automatiquement)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INT DEFAULT 0,
    lock_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
