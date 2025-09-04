# HeneriaLobby

## Commandes
- `/lobby setspawn` : définit le point de spawn du lobby.
- `/spawn` : téléporte le joueur au spawn du lobby.

## Permissions
- `lobby.admin.setspawn` : permettre de définir le spawn du lobby.
- `lobby.command.spawn` : permettre l'utilisation de `/spawn`.

## Comment compiler

Ce projet utilise Maven. Pour compiler le plugin et exécuter les tests, lancez :

```bash
mvn clean verify
```

Le fichier `.jar` généré se trouvera dans le dossier `target`.
