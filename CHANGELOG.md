# Changelog

## 0.5.1 - Interface de configuration en jeu
- Ajout de la commande `/lobbyadmin` pour configurer le parkour et le mini-foot.
- Sauvegarde immédiate des modifications dans `activities.yml`.

## 0.5.0 - Implémentation des activités du lobby
- Ajout du parkour chronométré avec checkpoints et classement holographique.
- Mini-jeu de mini-foot avec slime et détection de buts.
- Stand de tir à l'arc basique affichant le score.
- Nouveau fichier `activities.yml` pour configurer ces activités.

## 0.4.0 - Navigation par objets dédiés
- Ajout d'objets persistants configurables via `items.yml` pour ouvrir les menus principaux.
- Nouveau système de menus de premier niveau : jeux, profil, boutique et activités.
- Commandes `/games`, `/profil`, `/shop` et `/activites` comme raccourcis.

## 0.3.0 - Visual interface
- Added configurable scoreboard, tablist and chat formatting.
- Integrated LuckPerms for rank prefixes in tablist and chat.
- Added mention notifications with sound.

## 0.2.0 - Social system
- Added friend management and private messaging commands.
- New cross-server notifications for friend requests and status.

## 0.1.1 - Fix startup crash
- Shade HikariCP into the plugin jar to ensure database pool classes are available.

## 0.1.0 - Initial Setup
- Initial project structure.
- Database connection and player data management.
