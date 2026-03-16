# Guide TP1 – Serveur d'authentification « dangereux »

Ce guide détaille **tout le code à écrire** pour réaliser le TP1. Chaque section indique **où** modifier et **quoi** mettre (avant / après, ou ajouter).

**Objectif du TP1** : une première version fonctionnelle d’authentification (inscription, login, route protégée `/api/me`) avec mots de passe en clair et session HTTP. Version volontairement non sécurisée pour servir de base aux TP suivants.

---

## Vue d’ensemble du flux

1. **Inscription** : le client envoie `POST /api/auth/register` avec `email` et `password` → le serveur valide, vérifie l’unicité de l’email, enregistre en base (mot de passe en clair) → réponse 200 ou 400/409.
2. **Connexion** : le client envoie `POST /api/auth/login` avec `email` et `password` → le serveur vérifie et met l’utilisateur en **session** → réponse 200 ou 401.
3. **Route protégée** : le client envoie `GET /api/me` avec le **cookie de session** → le serveur lit l’utilisateur en session et renvoie `id` et `email`, ou 401 si non connecté.

**Comment lire le code** : le **contrôleur** reçoit la requête HTTP et appelle le **service** ; le **service** valide et utilise le **repository** pour la base ; les **exceptions** sont interceptées par le **ControllerAdvice** qui renvoie du JSON d’erreur.

---

## Étape 0 – Initialisation

### 0.1 Structure des packages (backend)

Créer sous `src/main/java/com/example/authentification/` les packages :

- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `exception`

**Explication** : cette structure sépare les couches (présentation → logique → accès données) et les types (entités, DTOs, exceptions).

### 0.2 README à la racine

Créer ou compléter le README principal du dépôt avec au minimum :

- Comment lancer MySQL et configurer `application.properties` (URL, user, mot de passe, création de la base).
- Comment lancer l’API : `mvn spring-boot:run` (ou via l’IDE).
- Comment lancer le client Java (JavaFX/Swing).
- Compte de test : `toto@example.com` / `pwd1234`.
- Section « Analyse de sécurité TP1 » avec au moins 5 risques (mot de passe en clair, règle faible, session simple, pas de protection brute force, pas de HTTPS, etc.).

**Tag Git** : `v1.0-init`.

---

## Étape 1 – Modèle de données

### 1.1 Script SQL – table `users`

**Fichier** : `authentification_back/src/main/resources/schema.sql`

**Action** : créer le fichier (ou remplacer son contenu) par :

```sql
-- id : clé primaire auto-incrémentée
-- email : unique, obligatoire
-- password_clear : mot de passe en clair (dangereux - TP1)
-- created_at : date de création
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_clear VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Explication** : `schema.sql` est exécuté au démarrage (si `spring.sql.init.mode=always`). On crée la table une seule fois ; `IF NOT EXISTS` évite l’erreur si elle existe déjà.

### 1.2 Entité JPA `User`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/entity/User.java`

**Action** : créer le fichier avec le contenu suivant.

```java
package com.example.authentification.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un utilisateur.
 * Mappe la table "users". Implémente Serializable pour stockage en session HTTP.
 * Cette implémentation est volontairement dangereuse et ne doit jamais être utilisée en production.
 */
@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_clear", nullable = false)
    private String passwordClear;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public User() {}

    public User(String email, String passwordClear) {
        this.email = email;
        this.passwordClear = passwordClear;
    }

    // Getters et setters pour id, email, passwordClear, createdAt
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordClear() { return passwordClear; }
    public void setPasswordClear(String passwordClear) { this.passwordClear = passwordClear; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

**Explication** : `@Entity` + `@Table(name = "users")` lient la classe à la table. `@Column(name = "password_clear")` mappe le champ Java `passwordClear` à la colonne `password_clear`. `@PrePersist` remplit `createdAt` avant chaque INSERT.

### 1.3 Repository `UserRepository`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/repository/UserRepository.java`

**Action** : créer le fichier.

```java
package com.example.authentification.repository;

import com.example.authentification.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository pour la table users. Spring Data génère l'implémentation.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

**Explication** : pas de code SQL à écrire ; Spring Data JPA implémente `findByEmail` et `existsByEmail` à partir du nom des méthodes.

**Tag Git** : `v1.1-model`.

---

## Étape 2 – Inscription

### 2.1 DTO `RegisterRequest`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/dto/RegisterRequest.java`

**Action** : créer le fichier.

```java
package com.example.authentification.dto;

/**
 * DTO pour POST /api/auth/register. Reçu en JSON : {"email":"...", "password":"..."}
 */
public record RegisterRequest(String email, String password) {}
```

**Explication** : un `record` est immuable ; Jackson désérialise le JSON vers ces champs automatiquement.

### 2.2 DTO `AuthResponse`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/dto/AuthResponse.java`

**Action** : créer le fichier.

```java
package com.example.authentification.dto;

/**
 * Réponse commune pour register et login : {"success": true, "message": "..."}
 */
public record AuthResponse(boolean success, String message) {}
```

### 2.3 Exceptions personnalisées

**Fichier** : `authentification_back/src/main/java/com/example/authentification/exception/InvalidInputException.java`

**Action** : créer.

```java
package com.example.authentification.exception;

/**
 * Données invalides (email vide, format incorrect, mot de passe trop court).
 * Cette implémentation est volontairement dangereuse et ne doit jamais être utilisée en production.
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) { super(message); }
    public InvalidInputException(String message, Throwable cause) { super(message, cause); }
}
```

**Fichier** : `authentification_back/src/main/java/com/example/authentification/exception/ResourceConflictException.java`

**Action** : créer.

```java
package com.example.authentification.exception;

/**
 * Conflit (ex. email déjà utilisé). Cette implémentation est volontairement dangereuse...
 */
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) { super(message); }
    public ResourceConflictException(String message, Throwable cause) { super(message, cause); }
}
```

**Explication** : le contrôleur et le service lancent ces exceptions ; le `@ControllerAdvice` les attrape et renvoie 400 ou 409.

### 2.4 `GlobalExceptionHandler`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/exception/GlobalExceptionHandler.java`

**Action** : créer.

```java
package com.example.authentification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(InvalidInputException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(request, HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleResourceConflict(ResourceConflictException ex,
                                                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(request, HttpStatus.CONFLICT, "Conflict", ex.getMessage()));
    }
}
```

**Explication** : tout `@RestController` qui lance `InvalidInputException` ou `ResourceConflictException` sera intercepté ici ; on renvoie un JSON avec `timestamp`, `status`, `error`, `message`, `path`.

### 2.5 Service `AuthService` – méthode `register`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Action** : créer le fichier (on ajoutera `login` à l’étape 3).

```java
package com.example.authentification.service;

import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.entity.User;
import com.example.authentification.exception.InvalidInputException;
import com.example.authentification.exception.ResourceConflictException;
import com.example.authentification.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * Service d'authentification. Cette implémentation est volontairement dangereuse et ne doit jamais être utilisée en production.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(RegisterRequest request) {
        validateEmail(request.email());
        validatePassword(request.password());

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Inscription échouée : email déjà existant pour {}", request.email());
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }

        User user = new User(request.email(), request.password());
        user = userRepository.save(user);
        log.info("Inscription réussie pour {}", request.email());
        return user;
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email est requis");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidInputException("Format d'email invalide");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidInputException("Le mot de passe doit contenir au minimum " + MIN_PASSWORD_LENGTH + " caractères");
        }
    }
}
```

**Explication** : la validation est centralisée dans le service. On ne logue **jamais** le mot de passe, seulement l’email en cas d’échec/succès.

### 2.6 Contrôleur `AuthController` – endpoint register

**Fichier** : `authentification_back/src/main/java/com/example/authentification/controller/AuthController.java`

**Action** : créer le fichier (on ajoutera `login` à l’étape 3).

```java
package com.example.authentification.controller;

import com.example.authentification.dto.AuthResponse;
import com.example.authentification.dto.RegisterRequest;
import com.example.authentification.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST /api/auth. Cette implémentation est volontairement dangereuse...
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new AuthResponse(true, "Inscription réussie"));
    }
}
```

**Explication** : `@RequestBody` désérialise le JSON en `RegisterRequest`. Si le service lève une exception, le `GlobalExceptionHandler` renvoie 400 ou 409.

### 2.7 Configuration et tests

- **application.properties** : s’assurer d’avoir l’URL MySQL, `spring.sql.init.mode=always`, `spring.sql.init.schema-locations=classpath:schema.sql` (et plus tard `data-locations` pour `data.sql`).
- **Tests** : ajouter les tests JUnit pour l’inscription (email vide, format incorrect, mot de passe trop court, inscription OK, email déjà existant → 409). Voir section « Tests TP1 » plus bas.

**Tag Git** : `v1.2-register`.

---

## Étape 3 – Connexion

### 3.1 DTO `LoginRequest`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/dto/LoginRequest.java`

**Action** : créer.

```java
package com.example.authentification.dto;

/**
 * DTO pour POST /api/auth/login. Reçu en JSON : {"email":"...", "password":"..."}
 */
public record LoginRequest(String email, String password) {}
```

### 3.2 Exception `AuthenticationFailedException`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/exception/AuthenticationFailedException.java`

**Action** : créer.

```java
package com.example.authentification.exception;

/**
 * Échec de login (email ou mot de passe incorrect). Message générique pour ne pas révéler si l'email existe.
 * Cette implémentation est volontairement dangereuse...
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) { super(message); }
    public AuthenticationFailedException(String message, Throwable cause) { super(message, cause); }
}
```

### 3.3 Gérer `AuthenticationFailedException` dans le ControllerAdvice

**Fichier** : `authentification_back/src/main/java/com/example/authentification/exception/GlobalExceptionHandler.java`

**Action** : ajouter ce handler (à côté des deux existants).

```java
@ExceptionHandler(AuthenticationFailedException.class)
public ResponseEntity<Map<String, Object>> handleAuthenticationFailed(AuthenticationFailedException ex,
                                                                      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(buildErrorResponse(request, HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage()));
}
```

### 3.4 Méthode `login` dans `AuthService`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Action** : ajouter la méthode suivante dans la classe `AuthService`.

```java
public User login(LoginRequest request) {
    validateEmail(request.email());
    validatePassword(request.password());

    User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> {
                log.warn("Connexion échouée : email inconnu {}", request.email());
                return new AuthenticationFailedException("Email ou mot de passe incorrect");
            });

    if (!user.getPasswordClear().equals(request.password())) {
        log.warn("Connexion échouée : mot de passe incorrect pour {}", request.email());
        throw new AuthenticationFailedException("Email ou mot de passe incorrect");
    }

    log.info("Connexion réussie pour {}", request.email());
    return user;
}
```

N’oubliez pas d’ajouter l’import : `import com.example.authentification.dto.LoginRequest;` et `import com.example.authentification.exception.AuthenticationFailedException;`.

### 3.5 Endpoint `login` dans `AuthController` + session

**Fichier** : `authentification_back/src/main/java/com/example/authentification/controller/AuthController.java`

**Modification 1** : ajouter en haut de la classe la constante et l’injection de la session si besoin :

```java
private static final String SESSION_USER = "authUser";
```

**Modification 2** : ajouter la méthode suivante (et l’import `jakarta.servlet.http.HttpSession` ainsi que `User` et `LoginRequest`) :

```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpSession session) {
    User user = authService.login(request);
    session.setAttribute(SESSION_USER, user);
    return ResponseEntity.ok(new AuthResponse(true, "Connexion réussie"));
}
```

**Explication** : après un login réussi, on stocke l’utilisateur en session. Le serveur envoie un cookie `JSESSIONID` ; le client le renverra automatiquement sur les requêtes suivantes (même origine).

### 3.6 Compte de test – `data.sql`

**Fichier** : `authentification_back/src/main/resources/data.sql`

**Action** : créer le fichier.

```sql
-- Compte de test obligatoire : toto@example.com / pwd1234
INSERT IGNORE INTO users (email, password_clear) VALUES ('toto@example.com', 'pwd1234');
```

Dans `application.properties`, ajouter :

```properties
spring.sql.init.data-locations=classpath:data.sql
```

**Explication** : `data.sql` est exécuté après `schema.sql`. `INSERT IGNORE` évite une erreur si la ligne existe déjà (redémarrage).

### 3.7 Tests

Ajouter les tests : login OK, login avec mot de passe incorrect → 401, login avec email inconnu → 401. Voir section « Tests TP1 ».

**Tag Git** : `v1.3-login`.

---

## Étape 4 – Route protégée GET /api/me

### 4.1 DTO `MeResponse`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/dto/MeResponse.java`

**Action** : créer.

```java
package com.example.authentification.dto;

/**
 * Réponse de GET /api/me. On n'expose jamais le mot de passe.
 */
public record MeResponse(Long id, String email) {}
```

### 4.2 Contrôleur `MeController`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/controller/MeController.java`

**Action** : créer.

```java
package com.example.authentification.controller;

import com.example.authentification.dto.MeResponse;
import com.example.authentification.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route protégée GET /api/me. Cette implémentation est volontairement dangereuse...
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private static final String SESSION_USER = "authUser";

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_USER);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail()));
    }
}
```

**Explication** : la clé de session `authUser` doit être la même que dans `AuthController`. Si aucun utilisateur en session → 401.

### 4.3 Tests

Ajouter : `GET /api/me` sans authentification → 401 ; après login avec la même session, `GET /api/me` → 200 et corps avec `id` et `email`. Voir « Tests TP1 ».

**Tag Git** : `v1.4-protected`.

---

## Étape 5 – Qualité : JavaDoc, logging, tests

### 5.1 JavaDoc

S’assurer que les JavaDoc sont présents et contiennent la phrase demandée sur les classes :

- `User`, `AuthService`, `AuthController`, `MeController`, exceptions.

Exemple de phrase : *« Cette implémentation est volontairement dangereuse et ne doit jamais être utilisée en production. »*

### 5.2 Logging dans un fichier

**Fichier** : `authentification_back/src/main/resources/logback-spring.xml`

**Action** : créer (ou compléter) pour écrire les logs en console et dans un fichier (ex. `logs/auth-server.log`). Ne **jamais** logger le mot de passe.

Exemple de structure :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>logs/auth-server.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

Les messages `log.info` / `log.warn` déjà présents dans `AuthService` (inscription/login) seront alors écrits dans ce fichier.

### 5.3 Récapitulatif des tests JUnit (minimum 8)

À placer dans une classe de test (ex. `AuthControllerTest`) avec `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`. Utiliser un profil **test** avec H2 en mémoire et `spring.sql.init.mode=never`, JPA en `create-drop` pour que les tables soient créées à partir des entités.

1. **Validation email** : register avec email vide → 400 ; register avec format email incorrect → 400.
2. **Validation mot de passe** : register avec mot de passe &lt; 4 caractères → 400.
3. **Inscription** : register valide → 200, `success: true`.
4. **Conflit** : register deux fois avec le même email → premier 200, second 409.
5. **Login** : après un register, login avec bons identifiants → 200.
6. **Login** : mot de passe incorrect → 401.
7. **Login** : email inconnu → 401.
8. **Route protégée** : GET /api/me sans session → 401 ; après login, GET /api/me avec la session du login → 200 et JSON avec `id` et `email`.

Pour les tests avec session, récupérer la session du `MvcResult` du `perform(post("/api/auth/login")...)` puis la passer à `perform(get("/api/me").session(session))`.

**Tag Git** : `v1-tp1`.

---

## Côté client (JavaFX) – TP1

### Vue générale

Le **client lourd** (JavaFX) se trouve dans le module `authentification_front` et communique avec le backend TP1 via HTTP :

- le FXML `auth-view.fxml` décrit l’interface (champs email/mot de passe, boutons, zone de profil) ;
- le contrôleur `AuthController` gère les clics et met à jour l’UI ;
- le client HTTP `AuthApiClient` envoie les requêtes `POST /api/auth/register`, `POST /api/auth/login` et `GET /api/me`.

L’idée est : **UI → AuthController → AuthApiClient → Backend**.

### FXML `auth-view.fxml`

**Fichier** : `authentification_front/src/main/resources/com/example/authentification_front/auth-view.fxml`

**Rôle** :

- panneau « Inscription » : champs `registerEmail`, `registerPassword`, bouton « S’inscrire », label `registerMessage` pour afficher la réponse ;
- panneau « Connexion » : champs `loginEmail`, `loginPassword`, bouton « Se connecter », label `loginMessage` ;
- panneau « Mon profil » (caché au début) : affiche l’`id` et l’`email` après un login réussi, boutons « Rafraîchir mon profil (GET /api/me) » et « Déconnexion ».

Chaque bouton déclenche une méthode du contrôleur (`onRegisterClick`, `onLoginClick`, `onRefreshMeClick`, `onLogoutClick`) via `onAction="#..."`.

### Contrôleur JavaFX `AuthController`

**Fichier** : `authentification_front/src/main/java/com/example/authentification_front/AuthController.java`

**Rôle** :

- récupérer le texte saisi dans les champs (`registerEmail`, `registerPassword`, `loginEmail`, `loginPassword`) ;
- faire une **validation minimale côté client** (email non vide, mot de passe non vide ou longueur ≥ 4) avant d’appeler le backend ;
- appeler `AuthApiClient.register` / `AuthApiClient.login` / `AuthApiClient.getMe` dans un thread séparé (`runAsync`) pour ne pas bloquer l’UI ;
- mettre à jour les labels de message (`registerMessage`, `loginMessage`) en vert ou rouge selon le succès/échec ;
- gérer l’affichage du panneau de profil (`profilPane`) et du texte `profilInfo` après `/api/me`.

**Flux typique d’inscription (clic sur « S’inscrire »)** :

1. `onRegisterClick` lit `registerEmail` et `registerPassword`.
2. Vérifie côté client : email non vide, mot de passe d’au moins 4 caractères.
3. Lance un thread (`runAsync`) qui appelle `apiClient.register(email, password)`.
4. Sur le thread JavaFX (`Platform.runLater`), affiche le message de succès ou d’erreur renvoyé par le backend.

**Flux typique de connexion (clic sur « Se connecter »)** :

1. `onLoginClick` lit `loginEmail` et `loginPassword`.
2. Vérifie que les deux sont non vides.
3. Lance un thread qui appelle `apiClient.login(email, password)`.
4. Si succès :
   - affiche le message de succès,
   - rend `profilPane` visible,
   - appelle `refreshProfil()` pour déclencher un `GET /api/me`.
5. Si échec : affiche le message d’erreur renvoyé par le backend (ex. 401, 400, 409 mis en forme par le `GlobalExceptionHandler`).

**Afficher les infos `/api/me`** :

- `refreshProfil()` appelle `apiClient.getMe()` dans un thread ;
- si succès 200 avec un `MeResponse`, on affiche :  
  `Id : <id> — Email : <email>` et un statut « Connecté. GET /api/me OK. » ;
- si 401 ou autre erreur, on affiche « Non connecté ou erreur : ... ».

**Déconnexion** :

- `onLogoutClick` recrée un `AuthApiClient` (donc un nouveau `CookieManager` sans cookies) ;
- cache le panneau profil et vide `profilInfo` ;
- met `statusLabel` à « Déconnecté. ».

### Client HTTP `AuthApiClient`

**Fichier** : `authentification_front/src/main/java/com/example/authentification_front/client/AuthApiClient.java`

**Rôle** :

- encapsuler les appels HTTP vers le backend TP1 ;
- gérer automatiquement les **cookies de session** grâce à `CookieManager` ;
- convertir les réponses JSON en objets Java avec **Gson** ;
- renvoyer un objet `ApiResult<T>` qui contient soit `data` (succès), soit `errorMessage` (erreur).

**Fonctionnement des méthodes** :

- `register(email, password)` :
  - construit un JSON `{"email":"...", "password":"..."}` ;
  - envoie `POST /api/auth/register` ;
  - si 2xx : parse `AuthResponse` et renvoie `ApiResult.ok(message)` ;
  - sinon : essaie de lire un JSON d’erreur (`message`) et renvoie `ApiResult.error(...)`.

- `login(email, password)` :
  - même principe que `register`, mais vers `POST /api/auth/login` ;
  - en cas de succès, le cookie `JSESSIONID` renvoyé par le serveur est stocké dans le `CookieManager` ;
  - ce cookie sera automatiquement renvoyé pour les appels suivants (notamment `GET /api/me`).

- `getMe()` :
  - envoie `GET /api/me` avec les cookies actuels (donc la session éventuellement authentifiée) ;
  - si 200 : parse en `MeResponse` (`id`, `email`) et renvoie `ApiResult.ok(...)` ;
  - si 401 : renvoie `ApiResult.error("Non authentifié.")` ;
  - sinon : renvoie `ApiResult.error("Erreur <code> : <body>")`.

**À retenir pour lire/recoder le front TP1** :

- le **backend** gère la logique métier (validation, base, session, erreurs HTTP) ;
- le **client JavaFX** ne fait qu’une **validation basique** et affiche ce que répond le backend ;
- l’**état connecté** est porté par la **session HTTP** (cookie `JSESSIONID`) gérée dans `AuthApiClient` via `CookieManager`.

---

## Résumé des fichiers créés/modifiés (TP1)

| Fichier | Action |
|---------|--------|
| `schema.sql` | Créer – table `users` |
| `data.sql` | Créer – compte toto@example.com |
| `application.properties` | Config MySQL, init SQL, session |
| `logback-spring.xml` | Créer – logs fichier + console |
| `entity/User.java` | Créer |
| `repository/UserRepository.java` | Créer |
| `dto/RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `MeResponse.java` | Créer |
| `exception/InvalidInputException.java`, `ResourceConflictException.java`, `AuthenticationFailedException.java` | Créer |
| `exception/GlobalExceptionHandler.java` | Créer – 3 handlers |
| `service/AuthService.java` | Créer – register + login + validation |
| `controller/AuthController.java` | Créer – POST register, POST login + session |
| `controller/MeController.java` | Créer – GET /api/me |
| `authentification_front/AuthController.java` | Utiliser les champs FXML, appeler AuthApiClient, afficher les messages |
| `authentification_front/client/AuthApiClient.java` | Gérer POST /register, POST /login, GET /me, cookies de session |
| Tests (profil test + H2) | Au moins 8 tests comme ci-dessus |

En suivant ce guide étape par étape et en remplaçant/ajoutant les blocs indiqués, vous obtenez une base TP1 complète et compréhensible pour la suite (TP2–TP4).
