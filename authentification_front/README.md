# Authentification Frontend – Client lourd TP1

Client lourd JavaFX pour l'API d'authentification (backend TP1). Permet de s'inscrire, se connecter et afficher le profil utilisateur via les endpoints REST.

---

## Sommaire

1. [Prérequis](#1-prérequis)
2. [Structure du projet](#2-structure-du-projet)
3. [Configuration](#3-configuration)
4. [Client API (AuthApiClient)](#4-client-api-authapiclient)
5. [Vue FXML (auth-view.fxml)](#5-vue-fxml-auth-viewfxml)
6. [Contrôleur (AuthController)](#6-contrôleur-authcontroller)
7. [Point d'entrée (HelloApplication)](#7-point-dentrée-helloapplication)
8. [Feuille de style (auth.css)](#8-feuille-de-style-authcss)
9. [Lancement](#9-lancement)
10. [Compte de test](#10-compte-de-test)

---

## 1. Prérequis pour le projet

- **Java 21** (ou 17+ avec JavaFX)
- **Maven**
- Le **backend** (`authentification_back`) doit tourner sur **http://localhost:8080** (voir le README du backend pour le lancer).

---

## 2. Structure du projet

Structure des fichiers **ajoutés ou modifiés** pour le client d'authentification TP1 :

```
src/main/java/com/example/authentification_front/
├── HelloApplication.java      # Point d'entrée (modifié : charge auth-view)
├── Launcher.java              # Lance l'application (existant)
├── AuthController.java        # Contrôleur de la vue d'auth (nouveau)
└── client/
    └── AuthApiClient.java     # Client HTTP vers l'API backend (nouveau)

src/main/resources/com/example/authentification_front/
├── auth-view.fxml             # Vue principale : inscription + connexion + profil (nouveau)
└── auth.css                  # Styles (titre, police) (nouveau)

src/main/java/
└── module-info.java          # Module Java (modifié : Gson, HTTP, opens client)
```

Les fichiers `hello-view.fxml` et `HelloController.java` d'origine restent présents mais ne sont plus utilisés au démarrage.

---

## 3. Configuration

### 3.1 `pom.xml`

**Objectif :** Déclarer les dépendances Maven du projet client.

**Explication :**
- **javafx-controls** et **javafx-fxml** : interface graphique JavaFX (fenêtre, boutons, champs, chargement FXML).
- **bootstrapfx-core** : thème optionnel pour JavaFX (peut être utilisé pour styliser les boutons/panneaux).
- **gson** : sérialisation / désérialisation JSON pour les requêtes et réponses de l'API (body `{"email":"...", "password":"..."}` et réponses du backend).
- **junit-jupiter** : tests unitaires (optionnel pour ce client).
- **javafx-maven-plugin** : permet de lancer l’app avec `mvn javafx:run` (mainClass = `HelloApplication`).

**Dépendance à ajouter** (si vous recréez le projet à partir d’un squelette sans Gson) :

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

Le reste du `pom.xml` (javafx-controls, javafx-fxml, maven-compiler source/target 21, javafx-maven-plugin avec mainClass `HelloApplication`) reste inchangé par rapport à un projet JavaFX standard.

### 3.2 `module-info.java`

**Objectif :** Déclarer les modules requis et ouvrir les packages nécessaires à JavaFX et à Gson.

**Explication :**
- **requires javafx.controls** et **requires javafx.fxml** : pour l’UI et le chargement FXML.
- **requires java.net.http** : pour `HttpClient` (appels HTTP vers le backend).
- **requires com.google.gson** : pour la conversion JSON.
- **opens com.example.authentification_front to javafx.fxml** : permet à JavaFX d’instancier le contrôleur et d’injecter les champs `@FXML`.
- **opens com.example.authentification_front.client to com.google.gson** : obligatoire pour que Gson puisse accéder par réflexion aux champs des classes du package `client` (notamment les DTOs internes comme `AuthRequest`). Sans cette ligne, une `InaccessibleObjectException` est levée lors de `gson.toJson(...)`.
- **exports com.example.authentification_front** : expose le package principal (pour un éventuel lanceur externe).

```java
module com.example.authentification_front {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.google.gson;

    requires org.kordamp.bootstrapfx.core;

    opens com.example.authentification_front to javafx.fxml;
    opens com.example.authentification_front.client to com.google.gson;

    exports com.example.authentification_front;
}
```

---

## 4. Client API (AuthApiClient)

**Objectif :** Centraliser les appels HTTP vers le backend (inscription, connexion, route protégée `/api/me`). Gérer les cookies de session pour que, après un login réussi, les requêtes suivantes (comme `GET /api/me`) envoient automatiquement le cookie `JSESSIONID`.

**Fichier :** `src/main/java/com/example/authentification_front/client/AuthApiClient.java`

**Explication :**
- **HttpClient** : construit avec un `CookieManager` et `CookiePolicy.ACCEPT_ALL` pour stocker les cookies renvoyés par le serveur (ex. `Set-Cookie: JSESSIONID=...`) et les renvoyer sur les requêtes suivantes. Ainsi, après `POST /api/auth/login`, le `GET /api/me` est envoyé avec le cookie et le backend reconnaît la session.
- **Base URL** : par défaut `http://localhost:8080`, modifiable via le constructeur `AuthApiClient(String baseUrl)`.
- **Gson** : utilisé pour convertir les objets en JSON (requêtes) et le JSON en objets (réponses). Les DTOs internes (`AuthRequest`, `AuthResponse`, `ErrorResponse`, `MeResponse`) correspondent aux formats attendus par le backend.
- **ApiResult&lt;T&gt;** : type de retour commun pour toutes les méthodes. Contient soit un succès (`success=true`, `data` renseigné), soit une erreur (`success=false`, `errorMessage`). Évite de propager des exceptions pour les erreurs métier ou réseau.
- **register(email, password)** : envoie `POST /api/auth/register` avec un body `{"email":"...", "password":"..."}`. Retourne le message de succès ou le message d’erreur (ex. 409 si email déjà utilisé).
- **login(email, password)** : envoie `POST /api/auth/login` avec le même format. En cas de succès, le cookie de session est stocké dans le `CookieManager` du client.
- **getMe()** : envoie `GET /api/me` (sans body). Si le client a une session valide, le backend renvoie 200 et un JSON `{"id":..., "email":"..."}` ; sinon 401. Les erreurs réseau ou 401 sont traduites en `ApiResult.error(...)`.
- **Déconnexion côté client** : il n’y a pas d’appel serveur. Pour « se déconnecter », on remplace le `AuthApiClient` par une nouvelle instance (comme dans `AuthController.onLogoutClick()`), ce qui « oublie » les cookies ; le prochain `GET /api/me` sera alors non authentifié.

**Extrait (construction du client et envoi d’une requête d’auth) :**

```java
public AuthApiClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(cookieManager)
            .build();
}

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
```

**DTOs internes (sérialisation JSON) :**
- **AuthRequest** : `email`, `password` — envoyé dans le body de register et login.
- **AuthResponse** : `success`, `message` — réponse 200 du backend pour register/login.
- **ErrorResponse** : champ `message` (avec `@SerializedName("message")`) — réponse 4xx du backend (400, 401, 409).
- **MeResponse** : `id`, `email` (champs publics) — réponse 200 de `GET /api/me`.
- **ApiResult&lt;T&gt;** : `success`, `data`, `errorMessage` — utilisé uniquement côté client pour le retour des méthodes.

---

## 5. Vue FXML (auth-view.fxml)

**Objectif :** Définir l’interface utilisateur (formulaires d’inscription et de connexion, zone profil, messages et statut) sans écrire le layout en Java. Le fichier est chargé par `HelloApplication` et associé au contrôleur `AuthController` via `fx:controller`.

**Fichier :** `src/main/resources/com/example/authentification_front/auth-view.fxml`

**Explication :**
- **VBox** : conteneur principal, alignement en haut au centre, espacement vertical entre les blocs. `styleClass="root"` pour appliquer les styles de `auth.css`.
- **TitledPane** : chaque section (Inscription, Connexion, Mon profil) est dans un panneau repliable pour garder la vue lisible.
- **Inscription** : `TextField` pour l’email (`fx:id="registerEmail"`), `PasswordField` pour le mot de passe (`registerPassword`), `Button` « S'inscrire » avec `onAction="#onRegisterClick"`, et un `Label` `registerMessage` pour afficher le succès ou l’erreur.
- **Connexion** : même idée avec `loginEmail`, `loginPassword`, bouton « Se connecter » (`#onLoginClick`), et `loginMessage`.
- **Mon profil** : `TitledPane` avec `fx:id="profilPane"` et **visible="false"** au départ. Le contrôleur le rend visible après une connexion réussie. Contient `profilInfo` (id + email) et deux boutons : « Rafraîchir mon profil (GET /api/me) » (`#onRefreshMeClick`) et « Déconnexion » (`#onLogoutClick`).
- **statusLabel** : en bas, pour le statut global (ex. « Prêt », « Connecté », « Déconnecté » ou message d’erreur).
- Les `fx:id` doivent correspondre exactement aux champs `@FXML` du contrôleur pour l’injection.

**Extrait (structure générale) :**

```xml
<VBox xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.example.authentification_front.AuthController"
      alignment="TOP_CENTER" spacing="20" styleClass="root">
    <padding><Insets top="25" right="30" bottom="25" left="30"/></padding>

    <Label text="Client d'authentification – TP1" styleClass="title-label"/>

    <TitledPane text="Inscription" expanded="true" collapsible="true">
        <VBox spacing="10">
            <!-- Email, PasswordField, Button S'inscrire, Label registerMessage -->
        </VBox>
    </TitledPane>

    <TitledPane text="Connexion" expanded="true" collapsible="true">
        <VBox spacing="10">
            <!-- Email, PasswordField, Button Se connecter, Label loginMessage -->
        </VBox>
    </TitledPane>

    <TitledPane fx:id="profilPane" text="Mon profil" visible="false">
        <VBox spacing="10">
            <Label fx:id="profilInfo" .../>
            <HBox spacing="10">
                <Button text="Rafraîchir mon profil (GET /api/me)" onAction="#onRefreshMeClick"/>
                <Button text="Déconnexion" onAction="#onLogoutClick"/>
            </HBox>
        </VBox>
    </TitledPane>

    <Label fx:id="statusLabel" wrapText="true" maxWidth="350"/>
</VBox>
```

---

## 6. Contrôleur (AuthController)

**Objectif :** Relier la vue FXML aux actions utilisateur et au client API. Valider les champs côté client (email non vide, mot de passe min. 4 caractères pour l’inscription), lancer les appels HTTP en arrière-plan pour ne pas bloquer l’UI, et mettre à jour les labels (messages, profil, statut).

**Fichier :** `src/main/java/com/example/authentification_front/AuthController.java`

**Explication :**
- **Champs @FXML** : tous les éléments interactifs et les labels de message sont injectés par le chargeur FXML à partir des `fx:id` de `auth-view.fxml`. Ne pas les initialiser à la main.
- **AuthApiClient** : une seule instance par contrôleur. Pour la déconnexion, on remplace par `new AuthApiClient()` pour perdre les cookies (aucun appel serveur de logout dans ce TP).
- **initialize()** : appelé après le chargement du FXML. On y met le message de statut initial (« Prêt. Assurez-vous que le backend... ») et on peut réinitialiser les messages d’erreur.
- **onRegisterClick** : lit email et mot de passe, vérifie email non vide et mot de passe ≥ 4 caractères. Lance `apiClient.register(...)` dans un thread séparé (`runAsync`), puis dans `Platform.runLater` met à jour `registerMessage` (vert si succès, rouge si erreur) et vide le mot de passe en cas de succès.
- **onLoginClick** : même principe avec email et mot de passe non vides. En cas de succès, affiche le panneau profil (`profilPane.setVisible(true)`) et appelle `refreshProfil()` pour remplir `profilInfo` via `GET /api/me`.
- **onRefreshMeClick** : appelle `refreshProfil()` pour refaire un `GET /api/me` et mettre à jour `profilInfo` et `statusLabel`.
- **onLogoutClick** : remplace `apiClient` par une nouvelle instance (cookies perdus), cache le panneau profil, réinitialise le texte du profil et met le statut à « Déconnecté ».
- **runAsync(Runnable)** : exécute la tâche dans un thread de fond (daemon) pour ne pas figer l’interface pendant les appels HTTP. Les mises à jour de l’UI doivent être faites dans `Platform.runLater(...)` car JavaFX n’autorise les changements de scène que depuis le thread JavaFX.
- **setMessage / clearMessage** : affichent un texte dans un `Label` avec une couleur (vert pour succès, rouge pour erreur) ou effacent le message.

**Extrait (inscription asynchrone) :**

```java
@FXML
protected void onRegisterClick() {
    String email = registerEmail.getText() == null ? "" : registerEmail.getText().trim();
    String password = registerPassword.getText() == null ? "" : registerPassword.getText();
    clearMessage(registerMessage);

    if (email.isEmpty()) {
        setMessage(registerMessage, "L'email est requis.", false);
        return;
    }
    if (password.length() < 4) {
        setMessage(registerMessage, "Le mot de passe doit contenir au minimum 4 caractères.", false);
        return;
    }

    runAsync(() -> {
        var result = apiClient.register(email, password);
        Platform.runLater(() -> {
            if (result.success) {
                setMessage(registerMessage, result.data, true);
                registerPassword.clear();
            } else {
                setMessage(registerMessage, result.errorMessage, false);
            }
        });
    });
}
```

---

## 7. Point d'entrée (HelloApplication)

**Objectif :** Démarrer l’application JavaFX et afficher la fenêtre d’authentification (auth-view) avec la feuille de style auth.css.

**Fichier :** `src/main/java/com/example/authentification_front/HelloApplication.java`

**Explication :**
- **start(Stage stage)** : charge `auth-view.fxml` via `HelloApplication.class.getResource("auth-view.fxml")` (le fichier doit être dans le même package que la classe, sous `src/main/resources`). Crée une `Scene` avec une taille initiale adaptée (ex. 400×580). Attache la feuille de style `auth.css` pour le titre et la racine. Définit le titre de la fenêtre et affiche la scène.

**Code :**

```java
@Override
public void start(Stage stage) throws IOException {
    FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("auth-view.fxml"));
    Scene scene = new Scene(fxmlLoader.load(), 400, 580);
    scene.getStylesheets().add(HelloApplication.class.getResource("auth.css").toExternalForm());
    stage.setTitle("Authentification TP1 – Client lourd");
    stage.setScene(scene);
    stage.show();
}
```

---

## 8. Feuille de style (auth.css)

**Objectif :** Appliquer un style simple à la racine et au titre pour que l’interface soit lisible sans dépendre uniquement du thème par défaut.

**Fichier :** `src/main/resources/com/example/authentification_front/auth.css`

**Explication :**
- **.root** : appliqué au nœud racine de la scène (ici la `VBox` avec `styleClass="root"`). Taille de police de base 14px.
- **.title-label** : appliqué au label « Client d'authentification – TP1 ». Police plus grande et en gras pour le titre.

```css
.root {
    -fx-font-size: 14px;
}
.title-label {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
}
```

---

## 9. Lancement

1. **Démarrer le backend**  
   Dans le projet `authentification_back`, lancer l’API (par ex. `mvn spring-boot:run` ou exécuter `AuthentificationApplication`). L’API doit être disponible sur **http://localhost:8080**.

2. **Lancer le client**  
   - **Maven** : à la racine de `authentification_front`, exécuter :
     ```bash
     mvn clean javafx:run
     ```
   - **IDE** : exécuter la classe **`HelloApplication`** (ou **`Launcher`** si configuré) comme point d’entrée.

3. **Utilisation**  
   - **Inscription** : saisir un email et un mot de passe (min. 4 caractères), cliquer sur « S'inscrire ». Le message sous le bouton indique le succès ou l’erreur (ex. email déjà utilisé).
   - **Connexion** : saisir email et mot de passe (ex. compte de test), cliquer sur « Se connecter ». En cas de succès, la section « Mon profil » s’affiche.
   - **Mon profil** : « Rafraîchir mon profil » envoie `GET /api/me` et affiche l’id et l’email. « Déconnexion » supprime les cookies côté client et cache le panneau profil.

---

## 10. Compte de test

Pour tester la connexion sans créer de compte :

- **Email :** `toto@example.com`
- **Mot de passe :** `pwd1234`

Ce compte est créé au démarrage du backend via `data.sql` (voir README du backend).

---

## Récapitulatif des fichiers à créer ou modifier (recodage)

| Fichier | Action |
|--------|--------|
| `pom.xml` | Ajouter la dépendance **gson** (voir section 3.1). |
| `module-info.java` | Ajouter `requires java.net.http`, `requires com.google.gson`, et **opens ...client to com.google.gson** (voir section 3.2). |
| `client/AuthApiClient.java` | Nouveau : client HTTP, CookieManager, register/login/getMe, DTOs, ApiResult (section 4). |
| `auth-view.fxml` | Nouveau : VBox, TitledPanes Inscription / Connexion / Mon profil, champs et boutons avec fx:id (section 5). |
| `AuthController.java` | Nouveau : champs @FXML, apiClient, onRegisterClick, onLoginClick, onRefreshMeClick, onLogoutClick, runAsync, refreshProfil (section 6). |
| `HelloApplication.java` | Modifier : charger `auth-view.fxml`, appliquer `auth.css`, titre « Authentification TP1 – Client lourd », dimensions de la scène (section 7). |
| `auth.css` | Nouveau : styles `.root` et `.title-label` (section 8). |

En suivant ce README et les extraits de code, vous pouvez recoder l’intégralité du client lourd TP1 à partir d’un projet JavaFX minimal.
