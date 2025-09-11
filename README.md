# HeneriaLobby

Core lobby plugin for the Heneria network. It connects to a shared MySQL/MariaDB database
and exposes a small API for other modules to access player information.

## Heneria UI – Final Menu Design

All interfaces follow the definitive "Heneria" charter:

- 54 slots with a border of `PURPLE_STAINED_GLASS_PANE` and a content zone in the
  middle.
- The last row is a universal navigation bar – slot `48` opens the player's
  profile, `49` shows the coin balance and `50` links to the cosmetic shop.
- Main menus close with a barrier in slot `53` while sub-menus expose a return
  arrow in slot `45` to fix the missing back-navigation bug.
- Titles use `§5§l`; default item names use `§d` or rarity colours. Key values are
  highlighted with `§6` (prices) and `§b` (stats), positive actions in `§a` and
  negative actions in `§c`. Descriptions remain in `§7`.

Example configurations are available in `menus.yml`.

## Building

The project uses Maven. To compile the plugin run:

```bash
mvn package
```

The compiled jar will be located in the `target` directory.

## Social Commands

The plugin provides network-wide social features:

- `/friends add <player>` – send a friend request.
- `/friends remove <player>` – remove a friend.
- `/friends accept <player>` – accept a pending request.
- `/friends deny <player>` – deny a pending request.
- `/friends list` – list your friends.
- `/msg <player> <message>` – send a private message.
- `/r <message>` – reply to the last player who messaged you.

## Système de Progression

Les joueurs accumulent des **Coins** en restant connectés ou en participant aux activités du lobby.
La commande `/coins` affiche le solde actuel.

Un système de **succès** configurable récompense les accomplissements.
Le fichier `achievements.yml` permet de définir chaque succès : identifiant, nom,
description, icônes verrouillé/déverrouillé, condition et récompenses (coins, titre, etc.).
Les joueurs peuvent consulter leur progression via le menu des succès
ou la commande `/achievements`.

## Configuration de l'Interface

Les fichiers de configuration permettent de personnaliser l'apparence du serveur :

- `scoreboard.yml` – titre et lignes du scoreboard. Exemple :

  ```yml
  title: "&d&lHENERIA"
  lines:
    - "&7&m------------"
    - "&fGrade: &d%luckperms_prefix%"
    - ""
    - "&fCoins: &6%player_coins%"
    - ""
    - "&fJoueurs: &a%server_online%/%server_max_players%"
    - ""
    - "&bplay.heneria.net"
    - "&7&m------------"
  ```
- `tablist.yml` – header, footer et format du nom dans la liste des joueurs.
- `messages.yml` – format du chat ainsi que les messages de connexion et de déconnexion.

Placeholders disponibles :

- `%player_coins%` – pièces du joueur.
- `%luckperms_prefix%` – préfixe de grade principal.
- `%server_online%` – joueurs connectés au lobby actuel.
- `%server_max_players%` – nombre maximum de joueurs du lobby.
- `{prefix}` – préfixe de grade fourni par LuckPerms.
- `{player}` – nom du joueur expéditeur.
- `{message}` – message envoyé.
- `{ping}` – ping du joueur pour le format de la tablist.

## Objets de Navigation Persistants

Le fichier `items.yml` distribue des objets permanents aux joueurs à leur connexion.
Pour chaque objet, on peut définir :

- `material` – type d'objet placé dans la barre d'inventaire.
- `slot` – position fixe dans l'inventaire.
- `name` et `lore` – texte supportant les codes couleur.
- `action` – généralement `open_menu:<nom>` pour ouvrir un menu spécifique.

Les menus correspondants peuvent aussi être ouverts via les commandes `/games` (`/jeux`),
`/profil`, `/shop` (`/boutique`) et `/activites`.

## Configuration des Menus

Le fichier `menus.yml` définit chaque interface : titre, taille et items internes avec
leur matériau, emplacement, nom, description et action au clic. Depuis la refonte
visuelle 2.0, les menus principaux adoptent un format standard de 54 slots avec
bordures en `PURPLE_STAINED_GLASS_PANE`, coins en `AMETHYST_BLOCK` et une barre de
navigation globale en bas de l'écran.

Chaque item peut spécifier un `slot` unique ou une liste `slots` pour être placé à
plusieurs positions. Les descriptions (`lore`) se définissent sur plusieurs lignes
grâce à une liste YAML et supportent les codes couleur.

Actions disponibles :

- `open_menu:<nom>` – ouvre un autre menu configuré.
- `connect_server:<serveur>` – envoie le joueur sur le serveur spécifié via Plugin Messages.
- `run_command:<commande>` – exécute une commande en tant que joueur.
- `close` – ferme simplement l'inventaire.

## Boutique de Cosmétiques

Le menu principal de la boutique regroupe plusieurs catégories
(Particules, Chapeaux, etc.). Cliquer sur une catégorie ouvre un
sous-menu paginé listant les cosmétiques définis dans `cosmetics.yml`.

Chaque item s'adapte à l'état du joueur :

- **Non possédé** – affiche la rareté, le prix en Coins et invite à
  acheter.
- **Débloqué** – l'item est enchanté, indique le statut "Débloqué" et
  permet de l'équiper ou de le déséquiper.

Le bouton **Mes Cosmétiques** ouvre l'inventaire personnel du joueur.
Seuls les cosmétiques possédés y apparaissent, triés par catégorie et
équipables d'un simple clic.

### Raretés des Cosmétiques

Un système visuel de rareté colore le nom de chaque item et affiche une
ligne d'étoiles dans sa description. Les valeurs par défaut sont définies
dans `rarities.yml` :

- **Commun** – `§a` : `§a★§7☆☆☆☆`
- **Rare** – `§9` : `§9★★§7☆☆☆`
- **Épique** – `§5` : `§5★★★§7☆☆`
- **Légendaire** – `§6` : `§6★★★★§7☆`
- **Mythique** – `§c` : `§c★★★★★`

### Ballons

Un ballon décoratif flotte derrière le joueur, attaché par une laisse à une
entité invisible qui le maintient en place.

### Familiers Animés

Les familiers apparaissent en version miniature lorsqu'une variante bébé existe
et suivent désormais naturellement le joueur grâce à leur IA interne. Leur
vitesse de déplacement peut être augmentée via la configuration
(`pets.speed-multiplier`) afin de suivre un joueur qui court. Ils restent
totalement inoffensifs et continuent d'afficher leurs effets visuels (pluie,
particules, etc.).

### Titres

Lorsqu'un titre est équipé, il s'affiche au-dessus du pseudo du joueur sans
masquer le nametag vanilla.

### Liste des Cosmétiques Disponibles

**Chapeaux**
- Commun : Bibliothèque, Casque de Cristal
- Rare : Chapeau Gâteau, Ruche d'Abeilles, Chapeau Explosif (TNT)
- Épique : Chapeau de Mineur, Enclume, Tête d'Enderman
- Légendaire : Tête de Dragon
- Mythique : Balise Céleste

**Particules**
- Commune : Goutte à Goutte
- Rare : Traînée de Cœurs, Notes de Musique
- Épique : Aura Enflammée, Poussière du Néant, Aura Magique
- Légendaire : Code Matrix, Vortex du Néant, Aura du Totem
- Mythique : Tempête du Wither

**Titres**
- Commun : L'Artisan, L'Apprenti
- Rare : Le Conquérant
- Épique : Le Fantôme, Le Titan
- Légendaire : La Légende
- Mythique : L'Élu(e)

**Familiers**
- Rare : Renard Rusé
- Épique : Golem de Fer miniature, Fée Tournoyante (animé)
- Légendaire : Allay Bienveillant, Nuage de Pluie (animé)
- Mythique : Âme en Peine (animé)

**Ballons**
- Rare : Ballon en Redstone
- Épique : Ballon Géode d'Améthyste
- Légendaire : Ballon Arc-en-ciel

**Messages d'Arrivée**
- Commun : Arrivée Classique
- Rare : Atterrissage
- Épique : Téléportation
- Légendaire : Annonce Royale
- Mythique : Faille Dimensionnelle

## Configuration des Activités

Le fichier `activities.yml` centralise la configuration des mini-jeux du lobby.
Pour chaque activité on peut définir les coordonnées des zones et les messages
affichés aux joueurs.

- **Parkour** : départ, checkpoints, arrivée et emplacement du classement holographique.
- **Mini-Foot** : point de réapparition du slime et zones de but.
- **Stand de Tir** : emplacements des blocs cible et message de score.

Les exemples fournis dans `activities.yml` peuvent servir de base et être
adaptés aux besoins de votre lobby.

## Administration en Jeu

Les administrateurs peuvent configurer les activités directement en jeu via
la commande `/lobbyadmin` (alias `/la`) :

- `/lobbyadmin parkour setspawn` – définit le point de départ du parkour.
- `/lobbyadmin parkour setend` – définit la plaque d'arrivée.
- `/lobbyadmin parkour addcheckpoint` – ajoute un checkpoint.
- `/lobbyadmin parkour removecheckpoint <numéro>` – supprime un checkpoint.
- `/lobbyadmin parkour listcheckpoints` – affiche les checkpoints.
- `/lobbyadmin minifoot setspawn` – définit le point de réapparition du slime.
- `/lobbyadmin minifoot setgoal1` – définit la première cage de but.
- `/lobbyadmin minifoot setgoal2` – définit la deuxième cage de but.

Chaque modification est immédiatement enregistrée dans `activities.yml`.

## Commandes d'Administration

Les administrateurs disposant de la permission `heneria.lobby.admin.eco` peuvent gérer la monnaie des joueurs via `/eco` :

- `/eco give <joueur> <montant>` – ajoute des Coins au joueur.
- `/eco take <joueur> <montant>` – retire des Coins au joueur.
- `/eco set <joueur> <montant>` – définit le solde exact du joueur.
- `/eco look <joueur>` – affiche le solde actuel du joueur.

Ces sous-commandes fonctionnent aussi bien pour les joueurs en ligne qu'hors ligne.
