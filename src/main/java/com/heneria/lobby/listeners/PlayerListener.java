package com.heneria.lobby.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.friends.FriendManager;
import com.heneria.lobby.friends.PrivateMessageManager;
import com.heneria.lobby.player.PlayerDataManager;
import com.heneria.lobby.ui.ScoreboardManager;
import com.heneria.lobby.ui.TablistManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final HeneriaLobbyPlugin plugin;
    private final PlayerDataManager dataManager;
    private final FriendManager friendManager;
    private final PrivateMessageManager messageManager;
    private final ScoreboardManager scoreboardManager;
    private final TablistManager tablistManager;

    public PlayerListener(HeneriaLobbyPlugin plugin, PlayerDataManager dataManager, FriendManager friendManager, PrivateMessageManager messageManager, ScoreboardManager scoreboardManager, TablistManager tablistManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.friendManager = friendManager;
        this.messageManager = messageManager;
        this.scoreboardManager = scoreboardManager;
        this.tablistManager = tablistManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(plugin.getMessages().getString("join-message").replace("{player}", player.getName()));
        dataManager.load(player.getUniqueId(), player.getName());
        friendManager.loadFriends(player.getUniqueId());
        scoreboardManager.show(player);
        tablistManager.update(player);
        for (UUID uuid : friendManager.getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage(plugin.getMessages().getString("friend-online").replace("{player}", player.getName()));
            } else {
                notifyProxy(player, uuid, true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.setQuitMessage(plugin.getMessages().getString("quit-message").replace("{player}", player.getName()));
        dataManager.save(player.getUniqueId());
        for (UUID uuid : friendManager.getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage(plugin.getMessages().getString("friend-offline").replace("{player}", player.getName()));
            } else {
                notifyProxy(player, uuid, false);
            }
        }
        friendManager.invalidate(player.getUniqueId());
        messageManager.clear(player.getUniqueId());
    }

    private void notifyProxy(Player player, UUID friend, boolean online) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(online ? "ONLINE" : "OFFLINE");
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(friend.toString());
        out.writeUTF(player.getName());
        player.sendPluginMessage(plugin, "heneria:friends", out.toByteArray());
    }
}
