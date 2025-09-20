package com.lobby.shop;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.utils.LogUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class HeadItemBuilder {

    private HeadItemBuilder() {
    }

    public static ItemStack createHeadItem(final String headId) {
        if (headId == null || headId.isBlank()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        final String trimmed = headId.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            final LobbyPlugin plugin = LobbyPlugin.getInstance();
            final HeadDatabaseManager manager = plugin != null ? plugin.getHeadDatabaseManager() : null;
            if (manager != null) {
                return manager.getHead(trimmed, Material.PLAYER_HEAD);
            }
            final String legacyId = trimmed.substring(4);
            if (!legacyId.isEmpty()) {
                final ItemStack databaseHead = getHeadFromDatabase(legacyId);
                if (databaseHead != null) {
                    return databaseHead;
                }
            }
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static ItemStack getHeadFromDatabase(final String headId) {
        try {
            final Plugin headDatabase = LobbyPlugin.getInstance() != null
                    ? LobbyPlugin.getInstance().getServer().getPluginManager().getPlugin("HeadDatabase")
                    : null;
            if (headDatabase == null || !headDatabase.isEnabled()) {
                return null;
            }
            final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            final Object apiInstance = apiClass.getMethod("getAPI").invoke(null);
            final Object result = apiInstance.getClass().getMethod("getItemHead", String.class).invoke(apiInstance, headId);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (final Exception exception) {
            LogUtils.warning(LobbyPlugin.getInstance(), "Error accessing HeadDatabase: " + exception.getMessage());
        }
        return null;
    }
}
