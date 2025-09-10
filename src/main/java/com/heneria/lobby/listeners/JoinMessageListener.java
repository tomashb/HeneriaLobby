package com.heneria.lobby.listeners;

import com.heneria.lobby.cosmetics.Cosmetic;
import com.heneria.lobby.cosmetics.CosmeticsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinMessageListener implements Listener {

    private final CosmeticsManager cosmeticsManager;

    public JoinMessageListener(CosmeticsManager cosmeticsManager) {
        this.cosmeticsManager = cosmeticsManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String equippedId = cosmeticsManager.getEquippedCosmeticId(player, "join_messages");
        if (equippedId == null) {
            return;
        }
        Cosmetic cosmetic = cosmeticsManager.getCosmeticById(equippedId);
        if (cosmetic == null) {
            return;
        }
        event.setJoinMessage(null);
        String message = cosmetic.getText().replace("%player%", player.getDisplayName());
        Bukkit.broadcastMessage(message);
    }
}
