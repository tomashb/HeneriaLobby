package com.heneria.lobby.cosmetics;

import org.bukkit.Material;
import org.bukkit.Particle;

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
    private final Particle particle;
    private final int particleCount;
    private final double particleOffset;

    public Cosmetic(String id, String name, List<String> lore, Material material,
                     String rarity, int price, String category, String text,
                     Particle particle, int particleCount, double particleOffset) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.material = material;
        this.rarity = rarity;
        this.price = price;
        this.category = category;
        this.text = text;
        this.particle = particle;
        this.particleCount = particleCount;
        this.particleOffset = particleOffset;
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

    public Particle getParticle() {
        return particle;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public double getParticleOffset() {
        return particleOffset;
    }
}
