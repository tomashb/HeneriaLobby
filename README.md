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
