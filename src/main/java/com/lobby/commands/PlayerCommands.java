package com.lobby.commands;

import com.lobby.lobby.LobbyManager;
import com.lobby.menus.MenuManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerCommands implements CommandExecutor, TabExecutor {

    private static final Map<String, String> COMMAND_MESSAGES = Map.of(
            "profil", "commands.profile_unavailable",
            "discord", "commands.discord_unavailable"
    );

    private static final Map<String, String> MENU_COMMANDS = Map.of(
            "serveurs", "servers_menu",
            "profil", "profil_menu"
    );

    private static final Map<String, String> LOBBY_MENU_SUBCOMMANDS = Map.of(
            "clan", "clan_menu",
            "friends", "friends_menu",
            "groups", "groups_menu"
    );

    private final LobbyManager lobbyManager;
    private final MenuManager menuManager;

    public PlayerCommands(final LobbyManager lobbyManager, final MenuManager menuManager) {
        this.lobbyManager = lobbyManager;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendConfigMessage(sender, "commands.player_only");
            return true;
        }

        final String commandName = command.getName().toLowerCase(Locale.ROOT);
        final Player player = (Player) sender;
        if (commandName.equals("lobby")) {
            handleLobbyCommand(player, args);
            return true;
        }
        if (MENU_COMMANDS.containsKey(commandName)) {
            final String menuId = MENU_COMMANDS.get(commandName);
            final boolean opened = menuManager != null && menuManager.openMenu(player, menuId);
            if (!opened) {
                final String messagePath = COMMAND_MESSAGES.getOrDefault(commandName, "commands.unavailable");
                MessageUtils.sendConfigMessage(player, messagePath, Map.of("command", "/" + label));
            }
            return true;
        }
        final String messagePath = COMMAND_MESSAGES.getOrDefault(commandName, "commands.unavailable");
        MessageUtils.sendConfigMessage(player, messagePath, Map.of("command", "/" + label));
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return Collections.emptyList();
    }

    private void handleLobbyCommand(final Player player, final String[] args) {
        if (args != null && args.length > 0) {
            final String argument = args[0].toLowerCase(Locale.ROOT);
            final String menuId = LOBBY_MENU_SUBCOMMANDS.get(argument);
            if (menuId != null) {
                final boolean opened = menuManager != null && menuManager.openMenu(player, menuId);
                if (!opened) {
                    MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", argument));
                }
                return;
            }
        }
        if (lobbyManager == null) {
            MessageUtils.sendConfigMessage(player, "lobby.teleport_failed");
            return;
        }
        final boolean hasSpawn = lobbyManager.hasLobbySpawn();
        final boolean teleported = lobbyManager.teleportToLobby(player);
        if (!lobbyManager.isBypassing(player)) {
            lobbyManager.preparePlayer(player, LobbyManager.PreparationCause.COMMAND);
        }
        if (!hasSpawn) {
            if (player.hasPermission("lobby.admin")) {
                MessageUtils.sendConfigMessage(player, "lobby.spawn_not_set");
            } else {
                MessageUtils.sendConfigMessage(player, "lobby.teleport_failed");
            }
            return;
        }
        if (teleported) {
            MessageUtils.sendConfigMessage(player, "lobby.teleport_success");
        } else if (player.hasPermission("lobby.admin")) {
            MessageUtils.sendConfigMessage(player, "lobby.spawn_not_set");
        } else {
            MessageUtils.sendConfigMessage(player, "lobby.teleport_failed");
        }
    }
}
