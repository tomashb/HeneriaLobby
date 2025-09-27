package com.lobby.friends.utils;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
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
    private Object headApiInstance;
    private Method getItemHeadMethod;
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
                final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                headApiInstance = apiClass.getDeclaredConstructor().newInstance();
                getItemHeadMethod = apiClass.getMethod("getItemHead", String.class);
                headDatabaseAvailable = true;
                logger.info("HeadDatabase détecté et disponible !");
                return true;
            }
            logger.info("HeadDatabase non détecté - utilisation des items par défaut");
        } catch (final ClassNotFoundException exception) {
            logger.warning("HeadDatabase API non trouvée - fonctionnement avec items par défaut");
        } catch (final Exception exception) {
            logger.warning("Erreur lors de l'initialisation de HeadDatabase: " + exception.getMessage());
        }
        headDatabaseAvailable = false;
        headApiInstance = null;
        getItemHeadMethod = null;
        return false;
    }

    public ItemStack createCustomHead(final String headId, final String name, final List<String> lore) {
        if (headDatabaseAvailable && headId != null && !headId.isBlank()) {
            try {
                ItemStack head = tryLoadHeadFromApi(headId);
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
        return createFallbackItem(name, lore, getMaterialFromHeadId(headId));
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

    public ItemStack createMappedItem(final String headId, final String name, final List<String> lore) {
        return createFallbackItem(name, lore, getMaterialFromHeadId(headId));
    }

    private ItemStack tryLoadHeadFromApi(final String headId) throws Exception {
        if (headApiInstance == null || getItemHeadMethod == null) {
            return null;
        }
        final Object result = getItemHeadMethod.invoke(headApiInstance, headId);
        if (result instanceof ItemStack itemStack) {
            return itemStack;
        }
        return null;
    }

    private Material getMaterialFromHeadId(final String headId) {
        if (headId == null || headId.isBlank()) {
            return Material.PLAYER_HEAD;
        }
        return switch (headId) {
            case "1420" -> Material.SPYGLASS;
            case "4579" -> Material.TRIPWIRE_HOOK;
            case "3644" -> Material.CLOCK;
            case "2118" -> Material.REDSTONE_LAMP;
            case "8665" -> Material.HEART_OF_THE_SEA;
            case "160" -> Material.BOOK;
            case "5568" -> Material.PAPER;
            case "4120" -> Material.BELL;
            case "2177" -> Material.WRITABLE_BOOK;
            case "7129" -> Material.NETHER_STAR;
            case "1393" -> Material.PLAYER_HEAD;
            case "3045" -> Material.MAP;
            case "5021" -> Material.DIAMOND;
            case "4654" -> Material.EMERALD;
            case "9056" -> Material.REDSTONE_BLOCK;
            case "1085" -> Material.LIME_DYE;
            case "622" -> Material.REDSTONE;
            case "2287" -> Material.FEATHER;
            default -> Material.PLAYER_HEAD;
        };
    }

    public boolean isHeadDatabaseAvailable() {
        return headDatabaseAvailable;
    }

    public String getHeadDatabaseStatus() {
        return headDatabaseAvailable
                ? "§a✅ HeadDatabase actif - Têtes personnalisées disponibles"
                : "§e⚠ HeadDatabase indisponible - Utilisation des matériaux mappés";
    }
}

