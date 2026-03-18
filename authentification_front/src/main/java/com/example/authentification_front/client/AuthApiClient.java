package com.example.authentification_front.client;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client HTTP pour communiquer avec l'API d'authentification (backend).
 * <p>
 * Objectifs principaux :
 * </p>
 * <ul>
 *     <li>encapsuler la logique d'appel HTTP (URL, headers, body JSON) pour les opérations
 *     d'inscription, de connexion et de récupération du profil,</li>
 *     <li>gérer automatiquement les cookies de session (notamment JSESSIONID) pour que le
 *     serveur reconnaisse l'utilisateur après le login,</li>
 *     <li>convertir les réponses JSON en objets Java simples via Gson.</li>
 * </ul>
 * <p>
 * La classe renvoie des {@code ApiResult<T>} qui encapsulent soit la donnée, soit un message d'erreur
 * lisible par le contrôleur JavaFX.
 * </p>
 * <p>
 * Implémentation volontairement simplifiée, non adaptée à un usage en production.
 * </p>
 */
public class AuthApiClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String ERROR_PREFIX = "Erreur";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public AuthApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public AuthApiClient(String baseUrl) {
        // On supprime un éventuel / final pour éviter les doublons lors de la concaténation
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // CookieManager accepte tous les cookies pour que la session HTTP fonctionne (JSESSIONID)
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        // HttpClient réutilisé pour toutes les requêtes du client lourd
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookieManager)
                .build();
    }

    /**
     * Inscription : POST /api/auth/register
     * <p>
     * Construit un JSON {@code {"email": "...", "password": "..."}}, l'envoie au backend
     * et parse la réponse standard {@code AuthResponse} ({@code success}, {@code message}).
     * </p>
     * @return message de succès ou d'erreur
     */
    public ApiResult<String> register(String email, String password) {
        String body = gson.toJson(new AuthRequest(email, password));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        return sendAuthRequest(request);
    }

    /**
     * Connexion : POST /api/auth/login
     * En cas de succès, le cookie de session est stocké pour les requêtes suivantes.
     * <p>
     * Comme pour {@link #register(String, String)}, le backend renvoie un {@code AuthResponse}.
     * Grâce au CookieManager, le cookie JSESSIONID renvoyé est automatiquement stocké et
     * réutilisé pour les appels suivants (notamment {@link #getMe()}).
     * </p>
     * @return message de succès ou d'erreur
     */
    public ApiResult<String> login(String email, String password) {
        String body = gson.toJson(new AuthRequest(email, password));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        return sendAuthRequest(request);
    }

    /**
     * Route protégée : GET /api/me (nécessite d'être connecté).
     * @return les infos utilisateur (id, email) ou null si non authentifié / erreur
     */
    public ApiResult<MeResponse> getMe() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/me"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                MeResponse me = gson.fromJson(response.body(), MeResponse.class);
                return ApiResult.ok(me);
            }
            if (response.statusCode() == 401) {
                return ApiResult.error("Non authentifié.");
            }
            return ApiResult.error(ERROR_PREFIX + " " + response.statusCode() + " : " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Requête interrompue", e);
        } catch (Exception e) {
            return ApiResult.error(ERROR_PREFIX + " réseau : " + e.getMessage());
        }
    }

    private ApiResult<String> sendAuthRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                AuthResponse auth = gson.fromJson(body, AuthResponse.class);
                return ApiResult.ok(auth != null ? auth.message : "OK");
            }
            return parseErrorResponse(body, response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Requête interrompue", e);
        } catch (Exception e) {
            return ApiResult.error(ERROR_PREFIX + " réseau : " + e.getMessage());
        }
    }

    private ApiResult<String> parseErrorResponse(String body, int statusCode) {
        try {
            ErrorResponse err = gson.fromJson(body, ErrorResponse.class);
            return ApiResult.error(err.message != null ? err.message : ERROR_PREFIX + " " + statusCode);
        } catch (Exception ignored) {
            return ApiResult.error(ERROR_PREFIX + " " + statusCode + " : " + body);
        }
    }

    // --- DTOs pour la sérialisation JSON ---

    private static class AuthRequest {
        final String email;
        final String password;

        AuthRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    private static class AuthResponse {
        boolean success;
        String message;
    }

    private static class ErrorResponse {
        @SerializedName("message")
        String message;
    }

    public static class MeResponse {
        private Long id;
        private String email;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ApiResult<T> {
        public final boolean success;
        public final T data;
        public final String errorMessage;

        private ApiResult(boolean success, T data, String errorMessage) {
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public static <T> ApiResult<T> ok(T data) {
            return new ApiResult<>(true, data, null);
        }

        public static <T> ApiResult<T> error(String message) {
            return new ApiResult<>(false, null, message);
        }
    }
}
