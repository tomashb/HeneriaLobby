package com.lobby.friends.utils;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lightweight helper around the HeadDatabase API used by the friends menus.
 * The class relies on the optional HeadDatabase plugin when available and
 * gracefully falls back to vanilla player heads when the dependency is not
 * installed on the server.
 */
public class HeadManager {

    private final LobbyPlugin plugin;
    private final HeadDatabaseManager lobbyHeadManager;
    private HeadDatabaseAPI headAPI;
    private boolean headDatabaseAvailable;

    public HeadManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.lobbyHeadManager = plugin != null ? plugin.getHeadDatabaseManager() : null;
        this.headDatabaseAvailable = initializeHeadDatabase();
    }

    private boolean initializeHeadDatabase() {
        if (plugin == null) {
            return false;
        }
        final Logger logger = plugin.getLogger();
        try {
            if (plugin.getServer().getPluginManager().getPlugin("HeadDatabase") != null) {
                headAPI = new HeadDatabaseAPI();
                headDatabaseAvailable = true;
                logger.info("HeadDatabase détecté et intégré avec succès !");
                return true;
            }
            logger.warning("HeadDatabase non détecté - utilisation des items par défaut");
        } catch (final Exception exception) {
            logger.warning("Erreur lors de l'initialisation de HeadDatabase: " + exception.getMessage());
        }
        headDatabaseAvailable = false;
        return false;
    }

    public ItemStack createCustomHead(final String headId, final String name, final List<String> lore) {
        if (headDatabaseAvailable && headId != null && !headId.isBlank()) {
            try {
                ItemStack head = null;
                if (headAPI != null) {
                    head = headAPI.getItemHead(headId);
                }
                if (head == null && lobbyHeadManager != null) {
                    head = lobbyHeadManager.getHead("hdb:" + headId, Material.PLAYER_HEAD);
                }
                if (head != null) {
                    final ItemStack clone = head.clone();
                    final ItemMeta meta = clone.getItemMeta();
                    if (meta != null) {
                        if (name != null) {
                            meta.setDisplayName(name);
                        }
                        if (lore != null && !lore.isEmpty()) {
                            meta.setLore(new ArrayList<>(lore));
                        }
                        clone.setItemMeta(meta);
                    }
                    return clone;
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Impossible de charger la tête HeadDatabase ID: " + headId + " - " + exception.getMessage());
            }
        }
        return createFallbackItem(name, lore, Material.PLAYER_HEAD);
    }

    public ItemStack createFallbackItem(final String name, final List<String> lore, final Material material) {
        final Material resolved = material != null ? material : Material.PLAYER_HEAD;
        final ItemStack item = new ItemStack(resolved);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(new ArrayList<>(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isHeadDatabaseAvailable() {
        return headDatabaseAvailable;
    }

    public String getHeadDatabaseStatus() {
        return headDatabaseAvailable
                ? "§a✅ HeadDatabase actif"
                : "§c❌ HeadDatabase indisponible - utilisation des items par défaut";
    }
}

