package com.lobby.data;

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
        boolean visible
) {

    public NPCData {
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    public NPCData withActions(final List<String> newActions) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, newActions, animation, visible);
    }

    public NPCData withArmorColor(final String newColor) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, newColor, actions, animation, visible);
    }

    public NPCData withAnimation(final String newAnimation) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, armorColor, actions, newAnimation, visible);
    }
}
