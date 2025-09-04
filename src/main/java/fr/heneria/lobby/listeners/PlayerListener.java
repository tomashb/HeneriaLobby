package fr.heneria.lobby.listeners;

import fr.heneria.lobby.LobbyPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final LobbyPlugin plugin;

    public PlayerListener(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn != null) {
            event.getPlayer().teleport(spawn);
        }
        plugin.getServerSelectorManager().giveItem(event.getPlayer());
    }

    @EventHandler
    public void onVoid(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn != null) {
            event.setCancelled(true);
            event.getEntity().teleport(spawn);
        }
    }
}
