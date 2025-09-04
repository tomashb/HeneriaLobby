# HeneriaLobby

## Fonctionnalités
- Sélecteur de serveurs via GUI avec item "Terre" dans le slot 0.

## Commandes
- `/lobby setspawn` : définit le point de spawn du lobby.
- `/spawn` : téléporte le joueur au spawn du lobby.
- `/servers` : ouvre le menu de sélection des serveurs.

## Permissions
- `lobby.admin.setspawn` : permettre de définir le spawn du lobby.
- `lobby.command.spawn` : permettre l'utilisation de `/spawn`.
- `lobby.command.servers` : permettre l'utilisation de `/servers`.

## Configuration

### `config.yml`
```yaml
server-selector-item:
  material: PLAYER_HEAD
  texture-value: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFkZDRmZTRhNDI5YWJkNjY1ZGZkYjNlMjEzMjFkMjIzNDuhOTE0Y2ZlMjFjZDVlY2Y4ZDk2ZjdjYjhhNTM3In19fQ=="
  slot: 0
  name: '&aSélecteur de Jeux'
  lore:
    - '&7Cliquez pour choisir votre jeu !'
```

### `server-selector.yml`
```yaml
menu-title: '&6Menu des jeux'
menu-size: 3
items:
  border-0: { slot: 0, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-1: { slot: 1, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-2: { slot: 2, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-6: { slot: 6, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-7: { slot: 7, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-8: { slot: 8, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-9: { slot: 9, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-17: { slot: 17, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-18: { slot: 18, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }
  border-26: { slot: 26, material: ORANGE_STAINED_GLASS_PANE, name: ' ' }

  bedwars:
    slot: 13
    material: HAY_BLOCK
    name: '&6Bedwars'
    lore:
      - '&7Détruisez le lit de vos adversaires !'
      - '&7Protégez le vôtre à tout prix.'
      - ''
      - '&eJoueurs en ligne: &f%bungee_bedwars%'
      - '&aCliquez pour rejoindre !'
    action: 'server:bedwars'

  zombie:
    slot: 21
    material: PURPUR_BLOCK
    name: '&2Zombie'
    lore:
      - '&7Combattez des hordes de zombies.'
      - '&7Survivez le plus longtemps possible.'
      - ''
      - '&eJoueurs en ligne: &f%bungee_zombie%'
      - '&aCliquez pour rejoindre !'
    action: 'server:zombie'

  nexus:
    slot: 22
    material: END_CRYSTAL
    name: '&5Nexus'
    lore:
      - '&7Défendez votre Nexus.'
      - '&7Détruisez celui des ennemis.'
      - ''
      - '&eJoueurs en ligne: &f%bungee_nexus%'
      - '&aCliquez pour rejoindre !'
    action: 'server:nexus'

  inedit:
    slot: 23
    material: DIAMOND_PICKAXE
    name: '&bMode Inédit'
    lore:
      - '&7Découvrez notre mode de jeu exclusif !'
      - '&7Une expérience unique et innovante.'
      - ''
      - '&eJoueurs en ligne: &f%bungee_inédit%'
      - '&aCliquez pour rejoindre !'
    action: 'server:inedit'
```

## Comment compiler

Ce projet utilise des dépôts Maven personnalisés pour ses dépendances : Spigot, PaperMC, PlaceholderAPI et Mojang Libraries (pour `com.mojang:authlib`). Pour compiler le plugin et exécuter les tests, lancez :

```bash
mvn clean verify
```

Le fichier `.jar` généré se trouvera dans le dossier `target`.
