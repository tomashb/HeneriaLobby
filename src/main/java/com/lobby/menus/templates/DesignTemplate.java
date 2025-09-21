package com.lobby.menus.templates;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DesignTemplate {

    private final String name;
    private final ConfigurationSection section;

    public DesignTemplate(final String name, final ConfigurationSection section) {
        this.name = name;
        this.section = section;
    }

    public String getName() {
        return name;
    }

    public String getFillMaterial() {
        if (section == null) {
            return null;
        }
        final ConfigurationSection fillSection = section.getConfigurationSection("fill");
        if (fillSection != null) {
            return fillSection.getString("material");
        }
        return section.getString("fill_material");
    }

    public List<Integer> getFillSlots() {
        if (section == null) {
            return List.of();
        }
        final ConfigurationSection fillSection = section.getConfigurationSection("fill");
        if (fillSection != null && fillSection.contains("slots")) {
            return fillSection.getIntegerList("slots");
        }
        if (section.contains("fill_slots")) {
            return section.getIntegerList("fill_slots");
        }
        return List.of();
    }

    public List<Map<?, ?>> getBorders() {
        if (section == null) {
            return List.of();
        }
        if (section.isList("borders")) {
            return section.getMapList("borders");
        }
        final ConfigurationSection bordersSection = section.getConfigurationSection("borders");
        if (bordersSection == null) {
            return List.of();
        }
        return bordersSection.getMapList("layers");
    }

    public ConfigurationSection getItemsSection() {
        if (section == null) {
            return null;
        }
        return section.getConfigurationSection("items");
    }

    public Map<String, Object> getRawValues() {
        if (section == null) {
            return Collections.emptyMap();
        }
        return section.getValues(false);
    }
}
