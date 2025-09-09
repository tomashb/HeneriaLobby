package com.heneria.lobby.listeners;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.ui.TablistManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Formats chat messages and handles mentions.
 */
public class ChatListener implements Listener {

    private final HeneriaLobbyPlugin plugin;
    private final TablistManager tablistManager;

    public ChatListener(HeneriaLobbyPlugin plugin, TablistManager tablistManager) {
        this.plugin = plugin;
        this.tablistManager = tablistManager;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        Player sender = event.getPlayer();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> broadcast(sender, message));
    }

    private void broadcast(Player sender, String message) {
        String format = plugin.getMessages().getString("chat-format", "{player}: {message}");
        String prefix = tablistManager.getPrefix(sender);
        for (Player target : Bukkit.getOnlinePlayers()) {
            String personal = message;
            boolean mentioned = personal.toLowerCase().contains("@" + target.getName().toLowerCase());
            if (mentioned) {
                personal = personal.replaceAll("(?i)@" + target.getName(), ChatColor.LIGHT_PURPLE + "@" + target.getName() + ChatColor.RESET);
            }
            String formatted = format
                    .replace("{prefix}", prefix)
                    .replace("{player}", sender.getName())
                    .replace("{message}", personal);
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', formatted));
            if (mentioned) {
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
        }
    }
}
