# Guide TP4 – Master Key + CI/CD (industrialisation)

Ce guide indique **quoi modifier ou ajouter** pour le TP4 : **chiffrement des mots de passe au repos** avec une **Master Key** (AES-GCM, variable d’environnement `APP_MASTER_KEY`), et **pipeline CI/CD** avec GitHub Actions (build, tests, SonarCloud, blocage des merges si échec). Les tests doivent tourner **sans MySQL** (H2 en mémoire) et la Master Key ne doit **jamais** être dans le code.

**Objectifs TP4** : mot de passe toujours chiffré en base ; Master Key fournie par `APP_MASTER_KEY` ; refus de démarrage si la clé est absente ; pipeline sur `push` et `pull_request` vers `main` ; Quality Gate vert ; tests autonomes avec H2 et clé de test.

---

## Partie 1 – Chiffrement des mots de passe par Master Key

### 1.1 Principe

- À l’**inscription** : mot de passe en clair → chiffrement AES-GCM avec la Master Key → stockage dans `password_encrypted`.
- Au **login** (HMAC TP3) : lecture de `password_encrypted` → déchiffrement avec la Master Key → mot de passe en clair → recalcul HMAC côté serveur et comparaison.
- La **Master Key** : jamais dans le code, jamais commitée. Elle est fournie par **variable d’environnement** `APP_MASTER_KEY`.
- Si `APP_MASTER_KEY` est **absente** au démarrage → l’application **refuse de démarrer** (ex. `@PostConstruct` qui lance une exception ou bean conditionnel).

### 1.2 Format de stockage recommandé

Format proposé : `v1:Base64(iv):Base64(ciphertext)`.

- **v1** : version du format (pour évolutions futures).
- **IV** (vecteur d’initialisation) : aléatoire pour chaque chiffrement (jamais d’IV fixe).
- **ciphertext** : sortie de AES-GCM (chiffrement + authentification).

**Règles** : pas de mode ECB ; pas d’IV fixe ; utiliser AES-GCM (chiffrement + intégrité).

### 1.3 Service de chiffrement

**Fichier** : `authentification_back/src/main/java/com/example/authentification/service/PasswordEncryptionService.java` (ou `CryptoService`)

**Action** : créer un service qui :

1. Lit la Master Key depuis l’environnement (ex. `System.getenv("APP_MASTER_KEY")` ou `@Value("${app.master.key}")` avec une config qui lit la variable d’environnement).
2. Au démarrage (ex. `@PostConstruct` ou dans un `ApplicationRunner`), vérifie que la clé est présente et non vide → sinon lancer une exception (ex. `IllegalStateException`) pour empêcher le démarrage.
3. Expose deux méthodes :
   - `String encrypt(String plainText)` : génère un IV aléatoire, chiffre avec AES-GCM, retourne la chaîne au format `v1:Base64(iv):Base64(ciphertext)`.
   - `String decrypt(String encrypted)` : parse le format, déchiffre avec la Master Key, retourne le texte clair.

**Exemple de structure (à adapter à votre projet)** :

```java
// Pseudocode pour l’idée
@PostConstruct
void checkMasterKey() {
    if (masterKey == null || masterKey.isBlank()) {
        throw new IllegalStateException("APP_MASTER_KEY must be set");
    }
}

public String encrypt(String plain) {
    byte[] iv = new byte[12]; // 12 bytes pour GCM
    new SecureRandom().nextBytes(iv);
    // Cipher AES/GCM/NoPadding, init avec key + iv, doFinal(plain.getBytes(UTF_8))
    // return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
}

public String decrypt(String encrypted) {
    // Split "v1:iv:ciphertext", decode Base64, init Cipher en DECRYPT, doFinal
    // return new String(plainBytes, UTF_8);
}
```

**Explication** : toute lecture/écriture du mot de passe en base passe par ce service. La clé n’existe que dans l’environnement d’exécution.

### 1.4 Intégration dans le flux métier

- **Inscription** : après validation (politique mot de passe), appeler `passwordEncryptionService.encrypt(request.password())` et stocker le résultat dans `User.passwordEncrypted` (ou le champ que vous utilisez).
- **Login (HMAC)** : récupérer `User` par email, appeler `passwordEncryptionService.decrypt(user.getPasswordEncrypted())` pour obtenir le mot de passe en clair, puis recalculer le HMAC et comparer comme en TP3.

La table `users` doit avoir le champ **password_encrypted** (pas de `password_clear` ni `password_hash` côté production ; en TP3 vous pouvez avoir déjà migré vers `password_encrypted`).

### 1.5 Refus de démarrage si clé absente

Déjà couvert au 1.3 : si `APP_MASTER_KEY` est absente, le bean de chiffrement (ou un `ApplicationRunner`) lève une exception au démarrage. Ainsi, l’application ne démarre jamais sans clé configurée.

### 1.6 Tests liés à la Master Key

- **Démarrage KO si `APP_MASTER_KEY` absente** : test d’intégration qui lance le contexte Spring sans définir la variable (ou avec profil qui ne la fournit pas) et attend une exception / échec de démarrage.
- **Chiffrement / déchiffrement** : avec une clé de test, `decrypt(encrypt(plain))` doit redonner `plain`.
- **password_encrypted ≠ password_plain** : après chiffrement, la chaîne stockée ne doit pas contenir le mot de passe en clair.
- **Déchiffrement KO si ciphertext modifié** : modifier un octet du ciphertext (ou du tag GCM) et vérifier que `decrypt` lève une exception (intégrité GCM).

Pour les tests, utiliser une **clé de test** fournie par **profil** ou **variable d’environnement** (ex. `APP_MASTER_KEY=test_master_key_for_ci_only` dans le profil `test` ou dans la config de la pipeline). Voir partie 2.

**Tag Git** (exemple) : `v4.1-master-key`.

---

## Partie 2 – CI/CD avec GitHub Actions

### 2.1 Objectifs

- Chaque **push** et chaque **pull_request** vers `main` déclenche :
  - Checkout du code.
  - Build (Maven).
  - Exécution des **tests JUnit**.
  - Analyse **SonarCloud**.
- **Échec** de la pipeline si : un test échoue ou le **Quality Gate** SonarCloud est rouge.
- Les tests doivent fonctionner **sans MySQL** : utiliser **H2 en mémoire** (profil `test`).
- La **Master Key** en CI : utiliser une clé **fictive** (variable d’environnement ou profil `application-test.properties`), jamais la clé de production.

### 2.2 Tests sans MySQL (H2)

**Fichier** : `authentification_back/src/test/resources/application-test.properties` (ou `application.properties` avec `spring.profiles.active=test`)

**Contenu type** :

```properties
# H2 en mémoire, mode MySQL pour compatibilité
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA : créer/supprimer les tables à partir des entités
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Ne pas exécuter schema.sql / data.sql (H2 géré par Hibernate)
spring.sql.init.mode=never

# Master Key pour les tests uniquement (jamais en prod)
APP_MASTER_KEY=test_master_key_for_ci_only
```

Si votre application lit `APP_MASTER_KEY` via `System.getenv()`, dans le test il faut que la variable soit définie. Vous pouvez soit la mettre dans ce fichier et l’injecter via un `Environment` ou une propriété personnalisée (ex. `app.master.key` mappée depuis `APP_MASTER_KEY` en prod et depuis une propriété en test), soit la définir dans le workflow GitHub Actions (voir ci-dessous).

**Explication** : en test, pas de MySQL à installer ; H2 crée une base en RAM. La Master Key de test ne sert qu’à faire passer les tests de chiffrement/déchiffrement et de login.

### 2.3 Workflow GitHub Actions

**Fichier** : `.github/workflows/ci.yml` (à la racine du dépôt, ou dans le module concerné selon votre structure)

**Exemple de pipeline** (à adapter : chemin du module Maven, JDK 17 si requis par le sujet) :

```yaml
name: CI
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Run tests with H2 and test Master Key
        working-directory: authentification_back
        env:
          APP_MASTER_KEY: test_master_key_for_ci_only
        run: mvn -B verify

      - name: SonarCloud analysis
        working-directory: authentification_back
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          APP_MASTER_KEY: test_master_key_for_ci_only
        run: mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=VOTRE_PROJECT_KEY -Dsonar.organization=VOTRE_ORG
```

**À adapter** :

- `working-directory` : si votre backend est à la racine, enlever `working-directory: authentification_back` et adapter les chemins.
- `SONAR_TOKEN`, `sonar.projectKey`, `sonar.organization` : selon votre projet SonarCloud. Les secrets sont à configurer dans **Settings → Secrets and variables → Actions** (SONAR_TOKEN).
- **Quality Gate** : dans SonarCloud, la pipeline doit échouer si le Quality Gate est rouge. C’est en général le comportement par défaut quand on lance le scanner Maven avec SonarCloud.

**Explication** : `mvn verify` exécute les tests ; si un test échoue, la job échoue. L’analyse SonarCloud s’exécute après ; si le Quality Gate est rouge, l’étape peut être configurée pour échouer (selon la config SonarCloud / plugin).

### 2.4 Bloquer les merges si la CI échoue

Dans **GitHub** : **Settings** du dépôt → **Branches** → règle de protection pour `main` → cocher **Require status checks to pass before merging** et sélectionner le statut de la job CI (ex. `build-and-analyze`). Ainsi, un PR ne peut pas être mergé tant que la pipeline n’est pas verte.

### 2.5 Secrets

- **SONAR_TOKEN** : créé dans SonarCloud (My Account → Security), puis ajouté dans GitHub comme secret du dépôt.
- **APP_MASTER_KEY** : ne **jamais** mettre la clé de production dans les secrets GitHub. En CI, utiliser uniquement la clé de test (en clair dans le workflow ou via un secret dédié « test only »).

**Tag Git** (exemple) : `v4.2-cicd`.

---

## Récapitulatif des modifications TP4

| Élément | Action |
|--------|--------|
| **Master Key** | Service de chiffrement (AES-GCM) ; lecture de `APP_MASTER_KEY` ; refus de démarrage si absente. |
| **Inscription** | Remplacer stockage direct par `passwordEncryptionService.encrypt(password)` puis stockage en base. |
| **Login (HMAC)** | Récupérer `password_encrypted`, `decrypt`, puis recalcul HMAC comme en TP3. |
| **Format stockage** | `v1:Base64(iv):Base64(ciphertext)` (pas d’IV fixe, pas d’ECB). |
| **Tests** | H2 + profil `test` ; `APP_MASTER_KEY` de test ; tests démarrage sans clé, encrypt/decrypt, intégrité. |
| **CI/CD** | Workflow sur push/PR vers `main` ; JDK 17 ; `mvn verify` ; SonarCloud ; env `APP_MASTER_KEY` de test. |
| **README** | Expliquer gestion de la Master Key (variable d’env, pas en dur), CI/CD (GitHub Actions, SonarCloud, blocage des merges), utilisation de H2 en test. |

En suivant ce guide, vous avez une base TP4 complète : mots de passe chiffrés au repos avec Master Key, et pipeline CI/CD qui impose build, tests et Quality Gate avant merge.
