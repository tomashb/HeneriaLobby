# Changelog

### Version 1.3.1 - Corrections de Compilation
- Correction de plusieurs erreurs de compilation dans le CosmeticsManager liées à l'utilisation de l'API (Particules, Spawn d'entités, Leashes).

### Version 1.3.0 - Refonte Cosmétique Majeure
- **CORRECTION CRITIQUE :** Le titre cosmétique s'affiche désormais correctement au-dessus du pseudo sans le masquer.
- **REWORK :** La catégorie "Gestes" a été supprimée et remplacée par la nouvelle catégorie "Ballons".
- **AJOUT :** Implémentation des Familiers Animés avec des comportements uniques.
- **CONTENU :** Ajout de nombreux nouveaux cosmétiques dans la boutique.

### Version 1.2.1 - Corrections Critiques des Cosmétiques
- L'achat d'un cosmétique ne l'équipe plus automatiquement.
- Correction majeure du bug où le titre masquait le pseudo du joueur.
- Les familiers sont désormais miniatures, inoffensifs et leur suivi a été amélioré.

## 1.1.0 - Refonte du système de rareté et ajout massif de contenu cosmétique
- Ajout de la rareté **Mythique** et configuration des couleurs/étoiles via `rarities.yml`.
- Les cosmétiques affichent désormais leur rareté colorée et une ligne d'étoiles.
- Ajout d'un grand nombre de nouveaux cosmétiques (chapeaux, particules, familiers, titres, transformations et emotes).

## 1.0.0 - Système de cosmétiques entièrement fonctionnel
- Ajout du menu "Mes Cosmétiques" pour gérer les cosmétiques débloqués.
- Effets visuels pour chapeaux, particules et titres avec gestion complète.

## 0.9.1 - Finalisation des cosmétiques et commandes d'administration de l'économie
- Gestion de l'équipement et du déséquipement des cosmétiques avec sauvegarde.
- Ajout de la commande `/eco` pour administrer les Coins des joueurs.

## 0.9.0 - Finalisation et refonte complète des sous-menus cosmétiques
- Sécurisation des menus cosmétiques et gestion complète des clics.
- Design dynamique des items selon l'état (bloqué, débloqué, équipé) avec enchantements et messages.
- Implémentation de l'achat et de l'équipement/déséquipement avec mise à jour instantanée.

## 0.8.0 - Ajout du contenu initial et finalisation des menus cosmétiques
- Ajout des sous-menus paginés pour chaque catégorie de cosmétiques.
- Possibilité d'acheter et d'équiper les cosmétiques directement depuis l'interface.
- Contenu initial des chapeaux, particules et titres.

## 0.7.2 - Refonte visuelle du menu cosmétique principal
- Menu de boutique cosmétique étendu à 45 slots avec bordures décoratives.
- Configuration des items entièrement personnalisable (matériaux, noms, lores).
- Ajout de l'action `close` et prise en charge de l'attribut `slots` dans `menus.yml`.

## 0.6.0 - Système de progression
- Ajout de l'économie de Coins avec gain passif et API interne.
- Commande `/coins` pour consulter son solde.
- Système de succès configurable via `achievements.yml` et menu dédié.

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
