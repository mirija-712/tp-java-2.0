-- =============================================================================
-- Data SQL : exécuté après schema.sql au démarrage
-- Objectif : insérer le compte de test obligatoire pour le TP1
-- INSERT IGNORE : ne fait rien si l'email existe déjà (évite les erreurs en redémarrage)
-- =============================================================================

-- Compte de test : toto@example.com / pwd1234
-- ATTENTION : la valeur ci-dessous doit être un hash BCrypt valide de "pwd1234".
-- Regénérer ce hash avec BCryptPasswordEncoder si nécessaire.
INSERT IGNORE INTO users (email, password_hash) VALUES ('toto@example.com', '$2a$10$wO5H8sO2jXh1Q2m3hLqvYeA0xXv6wJvFf9M5gkZQ3yQpZbVnYh2y');
