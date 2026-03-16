# Guide TP3 – Authentification forte (HMAC + nonce + timestamp, token)

Ce guide indique **quoi modifier ou ajouter** par rapport au TP2 pour réaliser le TP3. Le mot de passe **ne doit plus être envoyé** dans la requête de login : le client prouve qu’il connaît le secret avec une **signature HMAC** (email + nonce + timestamp), et le serveur vérifie sans jamais recevoir le mot de passe.

**Objectifs TP3** : protocole en 2 étapes (client envoie `email`, `nonce`, `timestamp`, `hmac` ; serveur vérifie timestamp, nonce anti-rejeu, recalcule HMAC avec le mot de passe déchiffré), émission d’un **token d’accès** pour `/api/me`, table `auth_nonce`, comparaison en temps constant, couverture tests ≥ 80 %, Quality Gate vert.

---

## Vue d’ensemble du protocole TP3

| Étape | Côté client | Côté serveur |
|-------|-------------|---------------|
| 1 | Saisie email + mot de passe. Génère `nonce` (UUID), `timestamp` (epoch secondes). Calcule `message = email + ":" + nonce + ":" + timestamp`, puis `hmac = HMAC_SHA256(password, message)`. Envoie `POST /api/auth/login` avec `{ "email", "nonce", "timestamp", "hmac" }`. | — |
| 2 | — | Vérifie email existant → sinon 401. Vérifie timestamp dans une fenêtre (± 60 s). Vérifie que le nonce n’a pas déjà été utilisé (table `auth_nonce`). Récupère le mot de passe en clair (déchiffrement, voir TP4 ; en TP3 on peut encore stocker réversible ou en clair pour la démo). Recalcule `hmac_expected = HMAC_SHA256(password_plain, message)`. Compare en **temps constant** avec `hmac` reçu → si différent, 401. Marque le nonce comme consommé. Génère un **token SSO** et renvoie `accessToken` + `expiresAt`. |
| 3 | Stocke `accessToken`, envoie `Authorization: Bearer <token>` sur `GET /api/me`. | `/api/me` vérifie le token au lieu de la session. |

**En TP3** : le sujet suppose un stockage **chiffré réversible** du mot de passe (déchiffré côté serveur pour recalculer le HMAC). En pratique, on peut préparer la structure (champ `password_encrypted`) et une clé de chiffrement simple ou une Master Key (TP4). Pour la compréhension du guide, on suppose qu’on a une méthode « récupérer le mot de passe en clair » (déchiffrement ou encore clair en base pour les premiers tests).

---

## Étape 1 – Base de données : table `auth_nonce`

### 1.1 Schéma SQL

**Fichier** : `authentification_back/src/main/resources/schema.sql`

**Action** : ajouter la création de la table `auth_nonce` (en plus de la table `users`). Le sujet impose une structure du type :

- `id`, `user_id`, `nonce`, `expires_at`, `consumed`, `created_at`
- Contrainte unique sur `(user_id, nonce)`.
- `expires_at` ≈ now + 2 minutes (TTL du nonce).

**Exemple** :

```sql
CREATE TABLE IF NOT EXISTS auth_nonce (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    nonce VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_nonce (user_id, nonce),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Explication** : un même nonce ne peut être utilisé qu’une fois par utilisateur ; après utilisation on met `consumed = true`. Les nonces expirés peuvent être purgés par un job ou ignorés à la lecture.

### 1.2 Entité JPA (optionnel si vous utilisez du SQL natif)

Si vous utilisez JPA pour le nonce, créer une entité `AuthNonce` avec les champs ci-dessus et un `AuthNonceRepository`. Sinon, utiliser un `JdbcTemplate` ou une requête native pour insérer/vérifier le nonce.

**Tag Git** : `v3.1-db-nonce`.

---

## Étape 2 – Côté client : calcul HMAC et envoi login

### 2.1 Payload login TP3

Le body du login n’est plus `{ "email", "password" }` mais `{ "email", "nonce", "timestamp", "hmac" }`.

**Côté client (Java)** :

1. Générer `nonce = UUID.randomUUID().toString()`.
2. Générer `timestamp = System.currentTimeMillis() / 1000` (epoch en secondes).
3. Construire `message = email + ":" + nonce + ":" + timestamp`.
4. Calculer `hmac = HMAC_SHA256(mot_de_passe_saisi, message)` (encodage du résultat en Base64 ou hex pour l’envoi JSON).
5. Envoyer `POST /api/auth/login` avec `{ "email", "nonce", "timestamp", "hmac" }`.

**Exemple de calcul HMAC en Java (client)** :

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public static String hmacSha256(String key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(secretKeySpec);
    byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hmacBytes);
}
```

Le client ne doit **jamais** envoyer le mot de passe en clair ; seul le HMAC est envoyé.

**Tag Git** : `v3.2-hmac-client`.

---

## Côté client (JavaFX) – TP3

Dans TP3, le front ne change pas visuellement beaucoup (tu peux garder les mêmes champs email/mot de passe), mais le **format de la requête de login** change complètement :

- tu n’envoies plus `{ "email", "password" }` ;
- tu envoies `{ "email", "nonce", "timestamp", "hmac" }`, où :
  - `nonce` est une valeur aléatoire unique, ex. `UUID.randomUUID().toString()` ;
  - `timestamp` est l’heure côté client en secondes (epoch) ;
  - `hmac` est la signature calculée à partir du **mot de passe saisi** + `email:nonce:timestamp`.

### 2.A – Adapter `AuthApiClient.login` pour TP3

**Fichier** : `authentification_front/src/main/java/com/example/authentification_front/client/AuthApiClient.java`

Actuellement, le login ressemble à :

```java
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
```

Pour TP3, il faut :

1. Générer un `nonce` et un `timestamp`.
2. Calculer `message = email + ":" + nonce + ":" + timestamp`.
3. Calculer `hmac = HMAC_SHA256(password, message)` (en Base64 par exemple).
4. Construire un `LoginHmacRequest` avec ces 4 champs.

Exemple (en remplaçant la méthode ou en en créant une nouvelle dédiée TP3) :

```java
public ApiResult<String> login(String email, String password) {
    try {
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis() / 1000L;
        String message = email + ":" + nonce + ":" + timestamp;
        String hmac = hmacSha256(password, message); // méthode utilitaire ci-dessous

        LoginHmacRequest payload = new LoginHmacRequest(email, nonce, timestamp, hmac);
        String body = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        return sendAuthRequest(request);
    } catch (Exception e) {
        return ApiResult.error(ERROR_PREFIX + " lors du calcul HMAC : " + e.getMessage());
    }
}
```

Ajouter les classes utiles dans `AuthApiClient` :

```java
private static class LoginHmacRequest {
    final String email;
    final String nonce;
    final long timestamp;
    final String hmac;

    LoginHmacRequest(String email, String nonce, long timestamp, String hmac) {
        this.email = email;
        this.nonce = nonce;
        this.timestamp = timestamp;
        this.hmac = hmac;
    }
}

private static String hmacSha256(String key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(secretKeySpec);
    byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hmacBytes);
}
```

Pense à importer les classes nécessaires (`UUID`, `Mac`, `SecretKeySpec`, `Base64`, etc.).

### 2.B – Stocker le token d’accès renvoyé par le backend

En TP3, si le login réussit, le serveur renvoie un **token d’accès** (par exemple sous forme de `LoginResponse` avec `accessToken`, `expiresAt`). Côté client :

- adapter `sendAuthRequest` pour, en cas de succès, lire ce DTO et le stocker quelque part dans `AuthApiClient` (champ `private String accessToken;`) ou dans le contrôleur ;
- modifier `getMe()` pour **envoyer le header `Authorization: Bearer <accessToken>`** au lieu de compter sur les cookies de session.

Exemple d’adaptation dans `getMe()` :

```java
public ApiResult<MeResponse> getMe() {
    if (accessToken == null || accessToken.isBlank()) {
        return ApiResult.error("Pas de token : veuillez vous reconnecter.");
    }

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/me"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();
    // ... suite inchangée (gestion 200 / 401 / erreurs)
}
```

Et, côté `login`, une fois la réponse JSON reçue, tu dois parser `LoginResponse` et mettre à jour `accessToken` :

```java
private ApiResult<String> sendAuthRequest(HttpRequest request) {
    try {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Exemple : si c'est la réponse du login TP3
            LoginResponse login = gson.fromJson(body, LoginResponse.class);
            if (login != null && login.accessToken != null) {
                this.accessToken = login.accessToken;
                return ApiResult.ok("Connexion réussie");
            }
            // Sinon, rester compatible avec AuthResponse classique
            AuthResponse auth = gson.fromJson(body, AuthResponse.class);
            return ApiResult.ok(auth != null ? auth.message : "OK");
        }
        return parseErrorResponse(body, response.statusCode());
    } catch (Exception e) {
        // ...
    }
}

private static class LoginResponse {
    String accessToken;
    String expiresAt;
}
```

### 2.C – Côté contrôleur JavaFX

**Fichier** : `authentification_front/src/main/java/com/example/authentification_front/AuthController.java`

Le contrôleur peut rester très proche de TP1/TP2 :

- `onLoginClick` continue de récupérer `loginEmail` et `loginPassword` puis appelle `apiClient.login(email, password)` ;
- si `result.success` est vrai, tu affiches « Connexion réussie » comme avant et appelles `refreshProfil()` ;
- `refreshProfil()` appelle maintenant `getMe()` qui utilise le **token** dans le header `Authorization` (vu ci-dessus).

Pour l’utilisateur, l’interface ne change presque pas, mais **la sécurité du protocole change complètement** : mot de passe jamais envoyé, HMAC, nonce, timestamp et token côté client.

## Étape 3 – Côté serveur : DTO login, vérification HMAC

### 3.1 Nouveau DTO pour le login TP3

**Fichier** : `authentification_back/src/main/java/com/example/authentification/dto/LoginRequest.java`

**Code actuel (TP2)** : `record LoginRequest(String email, String password)`.

**Remplacer par** (ou créer un nouveau DTO pour ne pas casser l’ancien) :

```java
/**
 * DTO pour POST /api/auth/login (protocole HMAC).
 * Le mot de passe n'est jamais envoyé ; le client envoie email, nonce, timestamp et la signature HMAC.
 */
public record LoginRequest(String email, String nonce, Long timestamp, String hmac) {}
```

Si vous gardez l’ancien `LoginRequest` pour des tests, vous pouvez avoir `LoginRequestHmac` ou simplement changer l’API pour n’accepter que ce nouveau format.

### 3.2 Vérification HMAC dans le service

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java` (ou un service dédié « HmacLoginService »)

**Logique** :

1. Vérifier que l’email existe → sinon 401.
2. Vérifier que le timestamp est dans une fenêtre acceptable (ex. ± 60 secondes par rapport à l’heure serveur) → sinon 401.
3. Vérifier le nonce anti-rejeu : si ce nonce a déjà été enregistré pour cet utilisateur (et éventuellement non expiré), refuser → 401.
4. Récupérer le mot de passe en clair de l’utilisateur (déchiffrement depuis `password_encrypted` en TP4 ; en TP3 vous pouvez encore avoir le mot de passe en clair ou un champ déchiffré pour les tests).
5. Recalculer le message : `message = email + ":" + nonce + ":" + timestamp`.
6. Recalculer `hmac_expected = HMAC_SHA256(password_plain, message)` (même algorithme et encodage que le client).
7. **Comparer en temps constant** : `hmac_expected` et `hmac` reçu (pour éviter les attaques par timing). Par ex. `MessageDigest.isEqual(hmac_expected.getBytes(), hmac_received.getBytes())` ou une méthode qui compare byte à byte sans court-circuit.
8. Si différent → 401.
9. Enregistrer le nonce comme utilisé (insert dans `auth_nonce` avec `consumed = true` ou mise à jour), avec `expires_at` ≈ now + 2 min.
10. Générer un token d’accès (voir étape 5) et retourner `accessToken` + `expiresAt`.

**Exemple utilitaire HMAC côté serveur (Java)** :

```java
public static byte[] hmacSha256(byte[] key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
}
```

Comparaison en temps constant (exemple) :

```java
import java.security.MessageDigest;
// ...
boolean valid = MessageDigest.isEqual(
    hmacExpectedBytes,
    Base64.getDecoder().decode(hmacReceived)
);
```

**Tag Git** : `v3.3-hmac-server`.

---

## Étape 4 – Anti-rejeu : gestion complète du nonce

### 4.1 Réserver le nonce avant de vérifier le HMAC

Pour éviter qu’un même nonce soit réutilisé :

- **Option A** : à la réception de la requête, insérer d’abord le nonce dans `auth_nonce` avec `consumed = false` et `expires_at = now + 120`. Si l’insert échoue (contrainte unique), c’est que le nonce a déjà été vu → 401.
- **Option B** : vérifier en base si `(user_id, nonce)` existe déjà (et non expiré) → si oui, 401. Sinon, après vérification HMAC réussie, insérer ou marquer comme consommé.

Le sujet demande de « réserver / enregistrer le nonce » et de « marquer le nonce comme consommé » après succès. Donc : soit vous insérez le nonce avant la vérification HMAC (et le marquez consommé après), soit vous vérifiez l’absence puis insérez après succès. L’important est qu’un nonce ne puisse servir qu’une fois et qu’il ait un TTL (ex. 120 s).

### 4.2 Expiration

À la vérification, ignorer ou supprimer les lignes où `expires_at < now()`. Vous pouvez faire un nettoyage périodique des vieux nonces en base.

**Tag Git** : `v3.4-anti-replay`.

---

## Étape 5 – Token d’accès et sécurisation de `/api/me`

### 5.1 Génération du token

Le serveur génère un token opaque (ex. UUID ou JWT) après un login HMAC réussi. Pour rester simple : `String accessToken = UUID.randomUUID().toString();` et stocker côté serveur l’association token → user_id (et éventuellement `expiresAt`) en base ou en cache. Durée de vie recommandée : 15 minutes.

### 5.2 Réponse login

**Fichier** : créer un DTO de réponse login, ex. `LoginResponse` ou étendre `AuthResponse`.

**Exemple** :

```java
public record LoginResponse(String accessToken, String expiresAt) {}
```

`expiresAt` peut être un ISO-8601 (ex. `Instant.now().plus(15, ChronoUnit.MINUTES).toString()`).

### 5.3 Endpoint login

**Fichier** : `authentification_back/src/main/java/com/example/authentification/controller/AuthController.java`

**Modification** : le `POST /api/auth/login` reçoit désormais le nouveau `LoginRequest` (email, nonce, timestamp, hmac). Il appelle le service de login HMAC et retourne `LoginResponse(accessToken, expiresAt)` au lieu de mettre l’utilisateur en session.

### 5.4 Protéger GET /api/me avec le token

**Fichier** : `authentification_back/src/main/java/com/example/authentification/controller/MeController.java`

**Modification** :

- Lire le header `Authorization: Bearer <accessToken>`.
- Valider le token (recherche en base/cache, vérifier expiration).
- Si invalide ou absent → 401.
- Sinon récupérer l’utilisateur associé et retourner `MeResponse(id, email)`.

**Explication** : plus de session HTTP pour `/api/me` ; l’état « connecté » est porté par le token. Le client JavaFX doit stocker `accessToken` après login et l’envoyer sur chaque appel à `/api/me`.

**Tag Git** : `v3.5-token`.

---

## Étape 6 – Table `users` et champ `password_encrypted`

Pour préparer le TP4 et respecter le sujet TP3 (mot de passe chiffré réversible), la table `users` doit avoir un champ **password_encrypted** au lieu de **password_hash** (BCrypt). En TP3 vous pouvez :

- Soit migrer dès maintenant : `password_hash` → `password_encrypted`, et stocker le mot de passe chiffré avec une clé (voir TP4). Au login, vous déchiffrez puis calculez le HMAC.
- Soit garder temporairement un stockage en clair ou un hash pour faire fonctionner les tests HMAC, et migrer en TP4.

Le sujet TP3 indique : « Le serveur stocke un mot de passe **chiffré réversible** en base » et « Une **Server Master Key (SMK)** est utilisée pour chiffrer/déchiffrer ». Donc en TP3 on peut introduire la **structure** (champ `password_encrypted`, table `auth_nonce`, logique HMAC + token) et en TP4 ajouter le chiffrement AES-GCM avec Master Key.

---

## Étape 7 – Tests et qualité (≥ 80 % couverture, Quality Gate)

### 7.1 Tests obligatoires (au moins 15)

- Login OK avec HMAC valide.
- Login KO si HMAC invalide.
- Login KO si timestamp expiré (trop dans le passé).
- Login KO si timestamp trop dans le futur.
- Login KO si nonce déjà utilisé (rejeu).
- Login KO si utilisateur inconnu.
- Comparaison en temps constant (test unitaire sur la fonction de comparaison).
- Token émis après login réussi et accès `/api/me` avec ce token → 200.
- Accès `/api/me` sans token → 401.

Ajouter les tests nécessaires pour atteindre **≥ 15 tests** et **couverture ≥ 80 %** (controller, service, nonce, token).

### 7.2 SonarCloud

- Quality Gate vert.
- Couverture ≥ 80 % (Jacoco).

### 7.3 JavaDoc

Documenter le protocole (HMAC, nonce, timestamp, anti-rejeu) et les **limites** du chiffrement réversible (pédagogique ; en production on préfère hash non réversible + bonnes pratiques).

**Tag Git** : `v3.6-tests-80` puis **`v3-tp3`**.

---

## Résumé des fichiers modifiés/ajoutés (TP3)

| Fichier / Composant | Action |
|---------------------|--------|
| `schema.sql` | Ajouter table `auth_nonce` ; éventuellement renommer `password_hash` → `password_encrypted` |
| Entité `AuthNonce` + repository | Créer si utilisation JPA |
| DTO `LoginRequest` | Remplacer par (email, nonce, timestamp, hmac) |
| DTO `LoginResponse` | Créer (accessToken, expiresAt) |
| Service (AuthService ou HmacLoginService) | Vérification timestamp, nonce, récupération mot de passe, HMAC, comparaison temps constant, émission token |
| AuthController login | Utiliser nouveau DTO, retourner LoginResponse |
| MeController | Lire `Authorization: Bearer`, valider token, retourner 401 ou 200 + MeResponse |
| Stockage token (table ou cache) | Associer token → user_id + expiration |
| Client (JavaFX) | Calcul HMAC, envoi (email, nonce, timestamp, hmac) ; stockage accessToken ; envoi Bearer sur /api/me |
| Tests | ≥ 15 tests dont ceux listés ci-dessus |

En suivant ce guide, vous obtenez le protocole d’authentification forte sans envoi du mot de passe, avec anti-rejeu et token d’accès pour `/api/me`.
