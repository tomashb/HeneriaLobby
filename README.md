# HeneriaLobby

Core lobby plugin for the Heneria network. It connects to a shared MySQL/MariaDB database
and exposes a small API for other modules to access player information.

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

- `scoreboard.yml` – titre et lignes du scoreboard.
- `tablist.yml` – header, footer et format du nom dans la liste des joueurs.
- `messages.yml` – format du chat ainsi que les messages de connexion et de déconnexion.

Placeholders disponibles :

- `%player_coins%` – pièces du joueur.
- `%total_players%` – nombre total de joueurs sur le réseau.
- `%lobby_players%` – joueurs connectés au lobby actuel.
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
leur matériau, emplacement, nom, description et action au clic.

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

### Familiers

Les familiers apparaissent en version miniature lorsqu'une variante bébé existe
et sont totalement inoffensifs. Ils suivent naturellement leur propriétaire.

### Titres

Lorsqu'un titre est équipé, il s'affiche au-dessus du pseudo du joueur sans
masquer le nametag vanilla.

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
