package com.lobby.menus.prompt;

import com.lobby.LobbyPlugin;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Handles temporary chat prompts triggered from menus.
 */
public class ChatPromptManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPrompt(final Player player,
                           final String message,
                           final BiConsumer<Player, String> handler) {
        if (player == null || handler == null) {
            return;
        }
        prompts.put(player.getUniqueId(), new Prompt(handler));
        player.closeInventory();
        if (message != null && !message.isBlank()) {
            player.sendMessage(MessageUtils.colorize(message));
        }
        player.sendMessage(MessageUtils.colorize("&7Tapez &ccancel &7pour annuler."));
    }

    public void cancelPrompt(final UUID uuid) {
        if (uuid != null) {
            prompts.remove(uuid);
        }
    }

    public boolean hasPrompt(final UUID uuid) {
        return uuid != null && prompts.containsKey(uuid);
    }

    @EventHandler
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        if (message != null && message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> player.sendMessage(MessageUtils.colorize("&cDemande annulée.")));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> prompt.handler().accept(player, message == null ? "" : message.trim()));
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        cancelPrompt(event.getPlayer().getUniqueId());
    }

    private record Prompt(BiConsumer<Player, String> handler) {
    }
}
