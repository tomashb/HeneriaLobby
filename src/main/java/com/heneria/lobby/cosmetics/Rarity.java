package com.heneria.lobby.cosmetics;

/**
 * Configuration for cosmetic rarity visuals.
 */
public class Rarity {
    private final String name;
    private final String color;
    private final String stars;

    public Rarity(String name, String color, String stars) {
        this.name = name;
        this.color = color;
        this.stars = stars;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getStars() {
        return stars;
    }
}
