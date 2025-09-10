package com.heneria.lobby.cosmetics;

import org.bukkit.Material;
import java.util.List;

/**
 * Represents a cosmetic item loaded from configuration.
 */
public class Cosmetic {
    private final String id;
    private final String name;
    private final List<String> lore;
    private final Material material;
    private final String rarity;
    private final int price;
    private final String category;
    private final String text;

    public Cosmetic(String id, String name, List<String> lore, Material material,
                     String rarity, int price, String category, String text) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.material = material;
        this.rarity = rarity;
        this.price = price;
        this.category = category;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public Material getMaterial() {
        return material;
    }

    public String getRarity() {
        return rarity;
    }

    public int getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public String getText() {
        return text;
    }
}
