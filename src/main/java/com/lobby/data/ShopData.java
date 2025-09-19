package com.lobby.data;

import java.util.List;

public final class ShopData {

    private ShopData() {
    }

    public record ShopCategoryData(
            String id,
            String displayName,
            String description,
            String iconMaterial,
            int sortOrder,
            boolean visible
    ) {
        public ShopCategoryData {
            id = id == null ? "" : id.trim();
            displayName = displayName == null ? id : displayName;
            description = description == null ? "" : description;
            iconMaterial = iconMaterial == null || iconMaterial.isBlank() ? "CHEST" : iconMaterial;
        }
    }

    public record ShopItemData(
            String id,
            String categoryId,
            String displayName,
            String description,
            String iconMaterial,
            String iconHeadTexture,
            long priceCoins,
            long priceTokens,
            List<String> commands,
            boolean confirmRequired,
            boolean enabled
    ) {
        public ShopItemData {
            id = id == null ? "" : id.trim();
            categoryId = categoryId == null ? "" : categoryId.trim();
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            description = description == null ? "" : description;
            iconMaterial = iconMaterial == null ? "PLAYER_HEAD" : iconMaterial;
            iconHeadTexture = iconHeadTexture == null || iconHeadTexture.isBlank() ? "hdb:35472" : iconHeadTexture;
            commands = List.copyOf(commands == null ? List.of() : commands);
        }
    }
}
