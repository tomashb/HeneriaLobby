# LobbyCore - Plugin Lobby Minecraft 1.21

LobbyCore est un plugin Paper moderne conçu pour fournir une base solide de lobby modulable pour les serveurs Minecraft 1.21.

## Installation
1. Téléchargez `LobbyCore-1.0.0-SNAPSHOT.jar` depuis les releases.
2. Placez le fichier dans le dossier `plugins/` de votre serveur Paper.
3. Configurez la base de données dans `config.yml` (MySQL ou fallback SQLite automatique).
4. Redémarrez votre serveur et profitez du lobby !

## Prérequis
- Minecraft 1.21+ (Paper)
- Java 21+
- MySQL 8.0+ (SQLite fallback)

## Développement
- Java 21
- Maven avec Shade plugin pour un JAR autonome
- Base de données gérée par HikariCP (MySQL ou SQLite)

## Fonctionnalités principales
- Architecture modulaire prête pour les modules Economy, Cosmetics, etc.
- Gestion automatique des configurations YAML (`config.yml`, `messages.yml`).
- Connexion MySQL avec fallback automatique sur SQLite.
- Gestion basique des données joueur (coins, tokens, temps de jeu).
- Événements d'entrée/sortie joueur et messages de bienvenue personnalisables.

## Scripts & CI/CD
- Workflow GitHub Actions `build-test.yml` pour compiler et exécuter les tests.
- Workflow GitHub Actions `release.yml` pour générer les artefacts lors des tags `v*.*.*`.

## Licence
Projet privé - Tous droits réservés.
