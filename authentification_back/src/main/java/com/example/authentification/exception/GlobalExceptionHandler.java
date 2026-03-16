package com.example.authentification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Gestionnaire global des exceptions (ControllerAdvice).
 * <p>
 * Objectif : intercepter toutes les exceptions levées par les contrôleurs et
 * renvoyer des réponses JSON cohérentes (timestamp, status, error, message, path).
 * Évite de dupliquer la gestion des erreurs dans chaque contrôleur.
 * </p>
 */
@RestControllerAdvice  // S'applique à tous les @RestController
public class GlobalExceptionHandler {

    // Construit un objet JSON d'erreur standard
    private Map<String, Object> buildErrorResponse(HttpServletRequest request, HttpStatus status,
                                                   String error, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", error,
                "message", message,
                "path", request.getRequestURI()
        );
    }

    // Données invalides -> HTTP 400 Bad Request
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(InvalidInputException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(request, HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage()));
    }

    // Login échoué -> HTTP 401 Unauthorized
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationFailed(AuthenticationFailedException ex,
                                                                          HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(request, HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage()));
    }

    // Email déjà existant -> HTTP 409 Conflict
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleResourceConflict(ResourceConflictException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(request, HttpStatus.CONFLICT, "Conflict", ex.getMessage()));
    }

    // Compte verrouillé (anti brute-force TP2) -> HTTP 423 Locked
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex,
                                                                   HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(buildErrorResponse(request, HttpStatus.LOCKED, "Locked", ex.getMessage()));
    }
}
