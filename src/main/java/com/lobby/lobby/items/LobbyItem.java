package com.lobby.lobby.items;

import org.bukkit.Material;

import java.util.List;

public record LobbyItem(
        String id,
        Material material,
        int slot,
        int amount,
        String displayName,
        List<String> lore,
        List<String> actions,
        String headId,
        Material fallbackMaterial,
        boolean glow,
        Integer customModelData
) {
}
