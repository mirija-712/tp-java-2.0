package com.example.authentification.exception;

/**
 * Exception levée lorsque le compte est temporairement verrouillé
 * après trop de tentatives de connexion échouées (anti brute-force TP2).
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }

    public AccountLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}

