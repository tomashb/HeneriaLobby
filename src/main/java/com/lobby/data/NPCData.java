package com.lobby.data;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record NPCData(
        String name,
        String displayName,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String headTexture,
        String armorColor,
        List<String> actions,
        String animation,
        boolean visible,
        ItemStack mainHandItem,
        ItemStack offHandItem
) {

    public NPCData {
        actions = List.copyOf(actions == null ? List.of() : actions);
        mainHandItem = cloneItem(mainHandItem);
        offHandItem = cloneItem(offHandItem);
    }

    public NPCData withActions(final List<String> newActions) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, newActions, animation,
                visible, mainHandItem, offHandItem);
    }

    public NPCData withArmorColor(final String newColor) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, newColor, actions, animation,
                visible, mainHandItem, offHandItem);
    }

    public NPCData withAnimation(final String newAnimation) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, actions, newAnimation,
                visible, mainHandItem, offHandItem);
    }

    public NPCData withMainHandItem(final ItemStack item) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, actions, animation,
                visible, item, offHandItem);
    }

    public NPCData withOffHandItem(final ItemStack item) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, actions, animation,
                visible, mainHandItem, item);
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return item == null ? null : item.clone();
    }
}
