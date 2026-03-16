# Guides des TPs – Serveur d'authentification

Ce dossier contient des **guides pas à pas** pour recoder le projet depuis le début. Chaque guide correspond à un TP et indique **exactement quel code ajouter ou modifier** pour atteindre la fonctionnalité demandée.

---

## Contenu des guides

| Fichier | Contenu |
|--------|---------|
| **[guide-tp1.md](guide-tp1.md)** | Serveur d'authentification « dangereux » : inscription, login, route protégée `/api/me`, session, exceptions, tests, JavaDoc, logs. |
| **[guide-tp2.md](guide-tp2.md)** | Authentification renforcée : politique mot de passe, BCrypt, anti brute-force (lockout), indicateur de force côté client, SonarCloud. |
| **[guide-tp3.md](guide-tp3.md)** | Authentification forte : protocole HMAC + nonce + timestamp (mot de passe jamais envoyé), token d'accès, anti-rejeu. |
| **[guide-tp4.md](guide-tp4.md)** | Industrialisation : chiffrement des mots de passe par Master Key (AES-GCM), CI/CD GitHub Actions, tests avec H2. |

---

## Comment utiliser ces guides

1. **Ordre recommandé**  
   Suivre TP1 → TP2 → TP3 → TP4. Chaque TP s’appuie sur le précédent.

2. **Format des modifications**  
   Dans chaque guide vous trouverez :
   - **« Code actuel »** ou **« Avant »** : extrait à modifier ou à remplacer.
   - **« Remplacer par »** ou **« Ajouter »** : nouveau code à mettre en place.
   - **Explications** : à quoi sert le code et comment le lire.

3. **Recoder depuis zéro**  
   - TP1 : le guide décrit la création du projet (packages, entité, repository, controller, service, DTOs, exceptions, tests, config).
   - TP2–TP4 : le guide indique quels fichiers modifier et quels blocs ajouter/remplacer.

4. **Comprendre le code**  
   Chaque section contient de courtes explications (rôle du fichier, flux de requête, pourquoi telle règle). Utilisez-les pour relire le code après l’avoir écrit.

---

## Structure du projet (rappel)

- **Backend** : `authentification_back/` — Spring Boot, API REST, JPA, MySQL (H2 en test).
- **Frontend** : `authentification_front/` — Client lourd Java (JavaFX).
- **Doc** : `Doc/` — Sujets des TPs (README-tp1 à tp4) et ces guides (guide-tp1 à tp4).

Les README-tp1.md à README-tp4.md décrivent les **objectifs et consignes** de chaque TP. Les **guide-tp1.md à guide-tp4.md** donnent le **détail du code** à écrire pour les atteindre.
