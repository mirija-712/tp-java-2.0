package com.example.authentification.service;

import com.example.authentification.dto.LoginRequest;
import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.entity.User;
import com.example.authentification.exception.AuthenticationFailedException;
import com.example.authentification.exception.AccountLockedException;
import com.example.authentification.exception.InvalidInputException;
import com.example.authentification.exception.ResourceConflictException;
import com.example.authentification.repository.UserRepository;
import com.example.authentification.validator.PasswordPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Service métier d'authentification (couche logique).
 * <p>
 * Objectif général :
 * </p>
 * <ul>
 *     <li>vérifier les données reçues du contrôleur (email, mot de passe),</li>
 *     <li>appliquer la politique de mot de passe du TP2 (longueur + complexité),</li>
 *     <li>parler au {@link com.example.authentification.repository.UserRepository} pour accéder à la base,</li>
 *     <li>gérer la sécurité de base : hashage BCrypt et protection anti brute-force avec verrouillage temporaire.</li>
 * </ul>
 * <p>
 * Le contrôleur REST se contente d'exposer les endpoints HTTP et délègue toute la logique ici.
 * </p>
 * <p>
 * Cette implémentation est volontairement pédagogique et ne doit jamais être utilisée telle quelle en production.
 * </p>
 */
@Service  // Bean Spring injectable
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");  // Format xxx@yyy.zzz

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Inscription d'un nouvel utilisateur.
     * Valide les données, vérifie l'unicité de l'email, puis sauvegarde.
     *
     * @param request email et mot de passe
     * @return l'utilisateur créé
     *
     * Étapes principales :
     * <ol>
     *     <li>valider le format de l'email,</li>
     *     <li>appliquer la politique de mot de passe TP2 (12 caractères, maj/min/chiffre/spécial),</li>
     *     <li>vérifier que l'email n'est pas déjà utilisé,</li>
     *     <li>hasher le mot de passe en BCrypt puis sauvegarder l'utilisateur,</li>
     *     <li>écrire un log d'information.</li>
     * </ol>
     */
    public User register(RegisterRequest request) {
        validateEmail(request.email());
        // Politique forte TP2 côté serveur
        PasswordPolicyValidator.validate(request.password());

        // Vérifier que l'email n'existe pas déjà
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Inscription échouée : email déjà existant pour {}", request.email());
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }

        // Créer et sauvegarder l'utilisateur avec un hash BCrypt du mot de passe
        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hashedPassword);
        user = userRepository.save(user);
        log.info("Inscription réussie pour {}", request.email());  // Ne JAMAIS logger le mot de passe
        return user;
    }

    /**
     * Connexion d'un utilisateur.
     * Vérifie que l'email existe et que le mot de passe correspond.
     *
     * @param request email et mot de passe
     * @return l'utilisateur authentifié
     *
     * Étapes principales :
     * <ol>
     *     <li>valider le format de l'email, refuser un mot de passe vide,</li>
     *     <li>charger l'utilisateur correspondant à l'email (ou lever AuthenticationFailedException),</li>
     *     <li>si un verrou existe encore (lockUntil dans le futur) &rarr; lever AccountLockedException (HTTP 423),</li>
     *     <li>si la période de verrouillage est passée &rarr; remettre à zéro les compteurs,</li>
     *     <li>comparer le mot de passe saisi avec le hash BCrypt stocké,</li>
     *     <li>en cas d'échec : incrémenter failedAttempts, verrouiller après 5 tentatives et lever l'exception adaptée,</li>
     *     <li>en cas de succès : réinitialiser failedAttempts et lockUntil puis retourner l'utilisateur.</li>
     * </ol>
     */
    public User login(LoginRequest request) {
        validateEmail(request.email());
        // Pas de politique complète au login, mais mot de passe non vide
        if (request.password() == null || request.password().isBlank()) {
            throw new InvalidInputException("Le mot de passe est requis");
        }

        // Chercher l'utilisateur par email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Connexion échouée : email inconnu {}", request.email());
                    return new AuthenticationFailedException("Email ou mot de passe incorrect");
                });
        LocalDateTime now = LocalDateTime.now();

        // Vérifier si le compte est verrouillé
        if (user.getLockUntil() != null && now.isBefore(user.getLockUntil())) {
            log.warn("Connexion refusée : compte verrouillé pour {}", request.email());
            throw new AccountLockedException("Compte temporairement verrouillé, veuillez réessayer plus tard");
        }

        // Si la période de verrouillage est passée, on réinitialise le compteur
        if (user.getLockUntil() != null && now.isAfter(user.getLockUntil())) {
            user.setFailedAttempts(0);
            user.setLockUntil(null);
        }

        // Comparer le mot de passe via BCrypt
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
            attempts++;
            user.setFailedAttempts(attempts);

            // Verrouiller après 5 échecs consécutifs pendant ~2 minutes
            if (attempts >= 5) {
                user.setLockUntil(now.plusMinutes(2));
                log.warn("Compte verrouillé après {} échecs pour {}", attempts, request.email());
                userRepository.save(user);
                throw new AccountLockedException("Compte temporairement verrouillé, veuillez réessayer plus tard");
            } else {
                log.warn("Connexion échouée (tentative {}): mot de passe incorrect pour {}", attempts, request.email());
                userRepository.save(user);
                throw new AuthenticationFailedException("Email ou mot de passe incorrect");
            }
        }

        // Succès : on réinitialise le verrouillage
        user.setFailedAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);

        log.info("Connexion réussie pour {}", request.email());
        return user;
    }

    // Valide le format de l'email (non vide, format xxx@yyy.zzz)
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email est requis");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidInputException("Format d'email invalide");
        }
    }
}
