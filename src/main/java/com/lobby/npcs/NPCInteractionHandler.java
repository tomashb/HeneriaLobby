package com.lobby.npcs;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.persistence.PersistentDataType;

public class NPCInteractionHandler implements Listener {

    private final NPCManager npcManager;

    public NPCInteractionHandler(final NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }
        final var key = npcManager.getNpcKey();
        if (key == null) {
            return;
        }
        final String npcName = armorStand.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (npcName == null) {
            return;
        }
        final NPC npc = npcManager.getNPC(npcName);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (npcManager.getMaxInteractionDistance() > 0) {
            if (!armorStand.getWorld().equals(player.getWorld())) {
                return;
            }
            final double maxDistance = npcManager.getMaxInteractionDistance();
            if (armorStand.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) {
                return;
            }
        }
        event.setCancelled(true);
        npc.handleInteraction(player, ClickType.RIGHT);
    }
}
