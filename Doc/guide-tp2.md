# Guide TP2 – Authentification renforcée (politique mot de passe, BCrypt, anti brute-force)

Ce guide indique **quoi modifier ou ajouter** par rapport au TP1 pour réaliser le TP2. On suppose que le code du TP1 est en place.

**Objectifs TP2** : politique de mot de passe stricte (12 caractères, majuscule, minuscule, chiffre, caractère spécial), stockage par **hash BCrypt** (plus de mot de passe en clair), **verrouillage** après 5 échecs de login (~2 min), indicateur de **force du mot de passe** côté client (rouge / orange / vert), et qualité avec **SonarCloud** (≥ 10 tests, couverture ≥ 60 %).

---

## Vue d’ensemble des changements

| Zone | TP1 | TP2 |
|------|-----|-----|
| Mot de passe en base | `password_clear` (clair) | `password_hash` (BCrypt) |
| Règle mot de passe serveur | min 4 caractères | min 12, 1 maj, 1 min, 1 chiffre, 1 spécial |
| Login | Comparaison en clair | `BCryptPasswordEncoder.matches(plain, hash)` |
| Nouveau | — | Compteur d’échecs + `lock_until` → 423 ou 429 après 5 échecs |
| Client inscription | 1 champ mot de passe | 2 champs (password + confirmation) + indicateur force |
| Qualité | 8 tests | ≥ 10 tests, SonarCloud, couverture ≥ 60 % |

**Comment lire les modifications** : on garde le même flux (register → login → /api/me), mais on change la **validation** (PasswordPolicyValidator), le **stockage** (hash), la **vérification** (BCrypt), et on ajoute une **couche anti brute-force** (entité ou champs User + logique dans AuthService).

---

## Étape 1 – Migration base : `password_clear` → `password_hash`

### 1.1 Schéma SQL

**Fichier** : `authentification_back/src/main/resources/schema.sql`

**Code actuel (TP1)** :

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_clear VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Remplacer par** (nouvelle table sans `password_clear`, avec `password_hash`) :

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Migration réelle** : si vous avez déjà des données en production, il faudrait un script de migration (ALTER TABLE, copie des données avec hash des anciens mots de passe). Pour le TP, repartir d’une base vide ou recréer la table suffit.

### 1.2 Entité `User`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/entity/User.java`

**Modifications** :

- Remplacer le champ `passwordClear` par `passwordHash` et la colonne `password_clear` par `password_hash`.
- Mettre à jour le constructeur, les getters/setters et le JavaDoc (plus de « mot de passe en clair », mais « hash du mot de passe »).

**Exemple** – remplacer :

```java
@Column(name = "password_clear", nullable = false)
private String passwordClear;
// ... getters/setters et constructeur avec passwordClear
```

**Par** :

```java
@Column(name = "password_hash", nullable = false)
private String passwordHash;
// Constructeur : User(String email, String passwordHash)
// getPasswordHash() / setPasswordHash()
```

**Explication** : en base on ne stocke que le hash BCrypt (irréversible). Le mot de passe en clair n’existe plus dans l’entité.

### 1.3 Données de test `data.sql`

**Fichier** : `authentification_back/src/main/resources/data.sql`

**Code actuel** :

```sql
INSERT IGNORE INTO users (email, password_clear) VALUES ('toto@example.com', 'pwd1234');
```

**Remplacer par** : un hash BCrypt du mot de passe `pwd1234`. Vous pouvez générer le hash une fois en Java (voir étape 3) ou avec un outil en ligne, puis l’insérer. Exemple (le hash ci-dessous est un exemple pour `pwd1234`, à régénérer si besoin) :

```sql
INSERT IGNORE INTO users (email, password_hash) VALUES ('toto@example.com', '$2a$10$...');
```

En pratique : au premier démarrage avec BCrypt en place, vous pouvez soit garder `data.sql` avec un hash valide pour `pwd1234`, soit supprimer l’INSERT et recréer le compte toto via l’API d’inscription (le service hashant alors le mot de passe).

**Tag Git** : `v2.1-db-migration`.

---

## Étape 2 – Politique de mot de passe stricte

### 2.1 Validateur côté serveur

**Fichier** : `authentification_back/src/main/java/com/example/authentification/validator/PasswordPolicyValidator.java` (créer le package `validator` si besoin)

**Action** : créer la classe.

```java
package com.example.authentification.validator;

import com.example.authentification.exception.InvalidInputException;
import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie que le mot de passe respecte la politique : 12 caractères min,
 * au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial.
 */
public final class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 12;

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
```

**Explication** : une seule méthode `validate(String)` ; en cas d’échec on lance `InvalidInputException` (donc 400 côté API). À utiliser à l’**inscription** et éventuellement au **changement de mot de passe**, pas au login (on ne reçoit que le mot de passe saisi, pas de « confirmation » côté serveur pour le login).

### 2.2 Utiliser le validateur dans `AuthService`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Modification** : dans `register`, remplacer l’ancienne validation mot de passe (longueur 4) par l’appel au nouveau validateur.

**Avant** (exemple TP1) :

```java
private static final int MIN_PASSWORD_LENGTH = 4;
// ...
private void validatePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
        throw new InvalidInputException("Le mot de passe doit contenir au minimum " + MIN_PASSWORD_LENGTH + " caractères");
    }
}
```

**Après** : supprimer `validatePassword` (ou la garder pour d’éventuels autres usages) et dans `register` appeler :

```java
PasswordPolicyValidator.validate(request.password());
```

Et supprimer l’appel à `validatePassword(request.password())` dans `register`. Pour le **login**, ne pas exiger la politique complète : on vérifie seulement que le mot de passe n’est pas null/vide si vous le souhaitez, ou rien de plus.

**Tag Git** : `v2.2-password-policy`.

---

## Étape 3 – Hashing BCrypt (inscription + login)

### 3.1 Dépendance Maven

**Fichier** : `authentification_back/pom.xml`

**Action** : ajouter la dépendance Spring Security (uniquement pour `BCryptPasswordEncoder`, pas besoin de configurer toute la sécurité) :

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

### 3.2 Configurer le bean `BCryptPasswordEncoder`

**Fichier** : créer une classe de configuration, par ex. `authentification_back/src/main/java/com/example/authentification/config/SecurityConfig.java` (package `config`)

**Action** : créer.

```java
package com.example.authentification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Explication** : ce bean sera injecté dans le service pour encoder à l’inscription et pour vérifier au login.

### 3.3 Inscription : hasher avant sauvegarde

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Modifications** :

- Injecter `PasswordEncoder` (constructeur ou champ).
- Dans `register`, après validation (email + politique mot de passe), hasher le mot de passe puis sauvegarder l’entité avec le hash.

**Avant** (TP1) :

```java
User user = new User(request.email(), request.password());
user = userRepository.save(user);
```

**Remplacer par** :

```java
String hashedPassword = passwordEncoder.encode(request.password());
User user = new User(request.email(), hashedPassword);
user = userRepository.save(user);
```

Adapter le constructeur de `User` pour qu’il prenne `(email, passwordHash)` (voir étape 1.2).

### 3.4 Login : vérifier avec BCrypt

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Avant** (TP1) :

```java
if (!user.getPasswordClear().equals(request.password())) {
    // ...
}
```

**Remplacer par** :

```java
if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
    // même message générique, même log.warn, même AuthenticationFailedException
}
```

**Explication** : `matches(plain, hash)` compare de façon sûre sans jamais stocker le clair. Même message d’erreur pour « email inconnu » et « mot de passe incorrect » (non-divulgation).

**Tag Git** : `v2.3-hashing`.

---

## Étape 4 – Anti brute-force (lockout)

### 4.1 Champs en base et entité

**Fichier** : `authentification_back/src/main/resources/schema.sql`

**Modification** : ajouter les colonnes pour le verrouillage.

**Remplacer** la définition de la table par (en gardant `password_hash`) :

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INT DEFAULT 0,
    lock_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Fichier** : `authentification_back/src/main/java/com/example/authentification/entity/User.java`

**Action** : ajouter deux champs (et getters/setters) :

```java
@Column(name = "failed_attempts")
private Integer failedAttempts = 0;

@Column(name = "lock_until")
private LocalDateTime lockUntil;
```

**Explication** : `failed_attempts` compte les échecs consécutifs ; `lock_until` indique jusqu’à quand le compte est bloqué. Après un login réussi, on remet `failed_attempts` à 0 et `lock_until` à null.

### 4.2 Exception (optionnel) pour « compte verrouillé »

Si vous voulez un message dédié, créer une exception par ex. `AccountLockedException` et la gérer dans le `GlobalExceptionHandler` avec un code **423 Locked** ou **429 Too Many Requests** (à justifier dans le README). Sinon, vous pouvez lever `AuthenticationFailedException` avec un message du type « Compte temporairement verrouillé » et renvoyer 423 depuis le handler.

**Exemple** – dans `GlobalExceptionHandler` :

```java
@ExceptionHandler(AccountLockedException.class)
public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex,
                                                                HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.LOCKED)  // 423
            .body(buildErrorResponse(request, HttpStatus.LOCKED, "Locked", ex.getMessage()));
}
```

### 4.3 Logique dans `AuthService.login`

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/AuthService.java`

**Idée** :

1. Récupérer l’utilisateur par email. S’il n’existe pas → même message qu’avant (401).
2. Si `lockUntil != null` et `LocalDateTime.now().isBefore(lockUntil)` → lever `AccountLockedException` (ou une exception gérée en 423/429).
3. Si maintenant &gt; `lockUntil`, déverrouiller : mettre `failed_attempts = 0`, `lock_until = null`, sauvegarder.
4. Vérifier le mot de passe avec `passwordEncoder.matches`. Si échec : incrémenter `failed_attempts`, si `failed_attempts >= 5` alors mettre `lock_until = now().plusMinutes(2)` (ou plus), sauvegarder, lever l’exception de verrouillage. Sinon lever `AuthenticationFailedException`.
5. Si succès : mettre `failed_attempts = 0`, `lock_until = null`, sauvegarder, retourner l’utilisateur.

**Explication** : on ne déverrouille qu’après expiration de `lock_until`. Les 5 échecs déclenchent un blocage d’environ 2 minutes (à documenter dans le README avec le choix 423 vs 429).

### 4.4 Tests

Ajouter au moins :  
- Login refusé avec code 423 (ou 429) quand le compte est verrouillé.  
- Après attente (ou en mockant l’heure), le compte se déverrouille et le login peut réussir.

**Tag Git** : `v2.4-lockout`.

---

## Étape 5 – Client : double saisie + indicateur de force

### 5.1 Inscription : deux champs mot de passe

**Côté client (JavaFX)** : ajouter un second champ `passwordConfirm` et vérifier que `password` et `passwordConfirm` sont égaux avant d’envoyer l’inscription. En cas d’inégalité, afficher un message d’erreur (ex. « Les mots de passe ne correspondent pas »).

### 5.2 Indicateur visuel de force (rouge / orange / vert)

- **Rouge** : non conforme (trop court ou règles non respectées).
- **Orange** : conforme mais faible (ex. juste le minimum).
- **Vert** : conforme et bon niveau (longueur + variété).

Implémentation possible : une fonction qui, à partir du mot de passe saisi, retourne un niveau (0, 1, 2) ou une énumération (WEAK, MEDIUM, STRONG), et qui met à jour la couleur d’un `Label` ou d’une barre (rouge / orange / vert) à chaque frappe (listener sur le champ mot de passe). Les critères doivent refléter la politique serveur (12 caractères, maj, min, chiffre, spécial).

**Tag Git** : `v2.5-ui-strength`.

---

## Côté client (JavaFX) – TP2

Dans TP2, l’interface reste la même structure (`auth-view.fxml`), mais tu améliores l’**inscription** :

- ajout d’un champ de **confirmation de mot de passe** ;
- ajout d’un indicateur de **force** (rouge/orange/vert) qui réagit en temps réel ;
- vérification côté client avant d’appeler le backend (confort utilisateur), tout en gardant la **validation forte côté serveur** (PasswordPolicyValidator).

### 5.A – Modifier le FXML d’inscription

**Fichier** : `authentification_front/src/main/resources/com/example/authentification_front/auth-view.fxml`

Dans le `TitledPane` « Inscription », tu peux :

- **ajouter** un `PasswordField` de confirmation `fx:id="registerPasswordConfirm"` ;
- **ajouter** un `Label` pour la force du mot de passe `fx:id="passwordStrengthLabel"`.

Exemple de bloc à adapter dans la partie Inscription :

```xml
<Label text="Mot de passe (min. 12 caractères) :"/>
<PasswordField fx:id="registerPassword" promptText="Mot de passe" prefWidth="280"/>

<Label text="Confirmer le mot de passe :"/>
<PasswordField fx:id="registerPasswordConfirm" promptText="Confirmation" prefWidth="280"/>

<Label fx:id="passwordStrengthLabel" text="Force du mot de passe" />

<Button text="S'inscrire" onAction="#onRegisterClick" defaultButton="true"/>
<Label fx:id="registerMessage" wrapText="true" maxWidth="300"/>
```

### 5.B – Adapter le contrôleur `AuthController`

**Fichier** : `authentification_front/src/main/java/com/example/authentification_front/AuthController.java`

1. **Nouveaux champs FXML** :

   Ajouter :

   ```java
   @FXML
   private PasswordField registerPasswordConfirm;

   @FXML
   private Label passwordStrengthLabel;
   ```

2. **Validation côté client dans `onRegisterClick`** :

   - vérifier que `registerPassword` et `registerPasswordConfirm` sont identiques ;
   - si non, afficher un message d’erreur dans `registerMessage` et ne pas appeler l’API ;
   - éventuellement refuser aussi si la longueur est manifestement trop courte (ex. < 8) même avant de laisser le serveur faire sa validation complète.

   Exemple d’adaptation de la méthode (pseudo-code) :

   ```java
   @FXML
   protected void onRegisterClick() {
       String email = ...;
       String password = ...;
       String confirm = registerPasswordConfirm.getText() == null ? "" : registerPasswordConfirm.getText();
       clearMessage(registerMessage);

       if (email.isEmpty()) {
           setMessage(registerMessage, "L'email est requis.", false);
           return;
       }
       if (!password.equals(confirm)) {
           setMessage(registerMessage, "Les mots de passe ne correspondent pas.", false);
           return;
       }

       // Optionnel : refuser Si < 8 caractères pour feedback rapide
       if (password.length() < 8) {
           setMessage(registerMessage, "Mot de passe trop court (min. 12 côté serveur).", false);
           return;
       }

       // Appel API comme avant (PasswordPolicyValidator fera la vraie validation côté serveur)
       runAsync(() -> {
           var result = apiClient.register(email, password);
           Platform.runLater(() -> { ... });
       });
   }
   ```

3. **Indicateur de force en temps réel**

   Dans `initialize()`, tu peux **ajouter un listener** sur le champ `registerPassword` pour mettre à jour `passwordStrengthLabel` à chaque frappe :

   ```java
   @FXML
   public void initialize() {
       clearMessage(registerMessage);
       clearMessage(loginMessage);
       statusLabel.setText("Prêt. Assurez-vous que le backend tourne sur http://localhost:8080");

       registerPassword.textProperty().addListener((obs, old, pwd) -> updatePasswordStrength(pwd));
   }
   ```

   Puis ajouter une méthode privée qui évalue la « force » en s’inspirant de la politique serveur (12 car., maj, min, chiffre, spécial) :

   ```java
   private void updatePasswordStrength(String password) {
       if (password == null || password.isEmpty()) {
           passwordStrengthLabel.setText("");
           passwordStrengthLabel.setStyle("");
           return;
       }

       int score = 0;
       if (password.length() >= 12) score++;
       if (password.chars().anyMatch(Character::isUpperCase)) score++;
       if (password.chars().anyMatch(Character::isLowerCase)) score++;
       if (password.chars().anyMatch(Character::isDigit)) score++;
       if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) score++;

       if (score <= 2) {
           passwordStrengthLabel.setText("Force : faible");
           passwordStrengthLabel.setStyle("-fx-text-fill: red;");
       } else if (score == 3 || score == 4) {
           passwordStrengthLabel.setText("Force : moyenne");
           passwordStrengthLabel.setStyle("-fx-text-fill: orange;");
       } else {
           passwordStrengthLabel.setText("Force : forte");
           passwordStrengthLabel.setStyle("-fx-text-fill: green;");
       }
   }
   ```

**À retenir pour le front TP2** :

- la **vraie règle de sécurité** reste côté serveur (PasswordPolicyValidator) ;
- le **front aide l’utilisateur** : il vérifie l’égalité des deux mots de passe et affiche une estimation de force visuelle avant même l’appel API ;
- tu gardes le même `AuthApiClient` (le format du JSON `{"email","password"}` ne change pas à ce stade).

## Étape 6 – SonarCloud et qualité

### 6.1 Configuration SonarCloud

- Créer le projet sur [SonarCloud](https://sonarcloud.io) et lier le dépôt GitHub.
- Dans le dépôt : **Settings → Secrets and variables → Actions** : ajouter `SONAR_TOKEN` (et éventuellement `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION` si utilisés dans le workflow).
- Fichier **sonar-project.properties** (à la racine du module backend ou du repo) avec au minimum :
  - `sonar.projectKey=...`
  - `sonar.organization=...`
  - (optionnel) `sonar.coverage.jacoco.reportPaths=...` pour la couverture.

### 6.2 Jacoco pour la couverture

**Fichier** : `authentification_back/pom.xml`

**Action** : ajouter le plugin Jacoco (report après les tests) pour que SonarCloud récupère la couverture. Exemple :

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Configurer Sonar pour pointer vers `target/site/jacoco/jacoco.xml` (ou le chemin indiqué dans votre version).

### 6.3 Objectifs qualité

- **Tests** : au moins 10 (incluant ceux du TP1 + non-divulgation erreur login + lockout qui expire).
- **Couverture** : viser ≥ 60 % pour le TP2.
- **JavaDoc** : sur les classes `security`, `validator`, `services`, `exceptions` ; dans `AuthService`, ajouter une phrase du type : « TP2 améliore le stockage mais ne protège pas encore contre le rejeu. »
- **Quality Gate** : corriger bugs majeurs et vulnérabilités majeures pour que le Quality Gate soit vert, ou justifier dans le README ce qui reste à faire pour le TP4.

**Tag Git** : `v2.6-sonarcloud`.

---

## Étape 7 – Finalisation

- Nettoyer les warnings, compléter la JavaDoc partout où demandé.
- Vérifier que tous les tests passent et que la couverture est ≥ 60 %.
- README : section « Qualité (TP2) » (faiblesse persistante de l’auth, config SonarCloud, résultat Quality Gate, couverture, plan d’améliorations).

**Tag Git** : `v2-tp2`.

---

## Résumé des fichiers modifiés/ajoutés (TP2)

| Fichier | Action |
|---------|--------|
| `schema.sql` | Remplacer `password_clear` par `password_hash` ; ajouter `failed_attempts`, `lock_until` |
| `data.sql` | Insérer avec `password_hash` (BCrypt pour toto) |
| `entity/User.java` | `passwordHash` + `failedAttempts`, `lockUntil` |
| `validator/PasswordPolicyValidator.java` | Créer |
| `config/SecurityConfig.java` | Créer – bean `BCryptPasswordEncoder` |
| `service/AuthService.java` | Utiliser PasswordPolicyValidator, PasswordEncoder, logique lockout |
| `exception/AccountLockedException.java` + `GlobalExceptionHandler` | Optionnel – 423/429 |
| Client (JavaFX) | Double champ mot de passe + indicateur force (rouge/orange/vert) |
| `pom.xml` | `spring-security-crypto`, Jacoco |
| SonarCloud + README | Config, Quality Gate, couverture ≥ 60 % |

En suivant ce guide en partant du TP1, vous obtenez une base TP2 complète : politique stricte, BCrypt, lockout, UI de force, et qualité SonarCloud.
