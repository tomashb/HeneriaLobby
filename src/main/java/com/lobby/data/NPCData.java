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
        List<String> actions,
        boolean visible
) {

    public NPCData {
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    public NPCData withActions(final List<String> newActions) {
        return new NPCData(name, displayName, world, x, y, z, yaw, pitch, headTexture, newActions, visible);
    }
}
