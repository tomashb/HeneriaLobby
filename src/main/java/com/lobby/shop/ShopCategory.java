package com.lobby.shop;

import com.lobby.data.ShopData.ShopCategoryData;

public class ShopCategory {

    private final ShopCategoryData data;

    public ShopCategory(final ShopCategoryData data) {
        this.data = data;
    }

    public ShopCategoryData getData() {
        return data;
    }

    public boolean isVisible() {
        return data.visible();
    }
}
