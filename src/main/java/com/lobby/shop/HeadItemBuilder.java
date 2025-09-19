package com.lobby.shop;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class HeadItemBuilder {

    private HeadItemBuilder() {
    }

    public static ItemStack createHeadItem(final String headId) {
        if (headId != null && headId.startsWith("hdb:")) {
            final String trimmed = headId.substring(4);
            if (!trimmed.isEmpty()) {
                final ItemStack databaseHead = getHeadFromDatabase(trimmed);
                if (databaseHead != null) {
                    return databaseHead;
                }
            }
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static ItemStack getHeadFromDatabase(final String headId) {
        try {
            final Plugin headDatabase = Bukkit.getPluginManager().getPlugin("HeadDatabase");
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
