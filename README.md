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
