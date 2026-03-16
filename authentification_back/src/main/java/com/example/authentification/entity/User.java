package com.example.authentification.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un utilisateur dans le système d'authentification.
 * <p>
 * Objectif : mapper la table "users" de la base de données en objet Java.
 * Chaque champ correspond à une colonne de la table.
 * Implémente {@link Serializable} pour permettre le stockage en session HTTP.
 * </p>
 * <p>
 * Cette implémentation est volontairement dangereuse et ne doit jamais être utilisée en production.
 * Les mots de passe sont stockés sous forme de hash dans le champ {@code password_hash},
 * et des champs supplémentaires permettent de compter les échecs de connexion (TP2).
 * </p>
 */
@Entity
@Table(name = "users")  // Nom de la table en base de données
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id  // Clé primaire
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Auto-incrémenté par la BDD
    private Long id;

    @Column(nullable = false, unique = true)  // Obligatoire et unique (pas de doublon)
    private String email;

    @Column(name = "password_hash", nullable = false)  // Hash BCrypt du mot de passe
    private String passwordHash;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // S'exécute automatiquement avant chaque INSERT en base
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public User() {}  // Constructeur vide requis par JPA

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(LocalDateTime lockUntil) {
        this.lockUntil = lockUntil;
    }
}
