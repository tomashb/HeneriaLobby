package com.lobby.shop;

import com.lobby.data.ShopData.ShopItemData;

public class ShopItem {

    private final ShopItemData data;

    public ShopItem(final ShopItemData data) {
        this.data = data;
    }

    public ShopItemData getData() {
        return data;
    }

    public boolean isEnabled() {
        return data.enabled();
    }
}
