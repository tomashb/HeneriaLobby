package com.lobby.data;

import java.util.List;

public record HologramData(
        String name,
        String world,
        double x,
        double y,
        double z,
        List<String> lines,
        boolean visible,
        AnimationType animation
) {

    public enum AnimationType {
        NONE,
        FLOAT,
        ROTATE
    }

    public HologramData withLines(final List<String> newLines) {
        return new HologramData(name, world, x, y, z, newLines, visible, animation);
    }

    public HologramData withLocation(final String newWorld, final double newX, final double newY, final double newZ) {
        return new HologramData(name, newWorld, newX, newY, newZ, lines, visible, animation);
    }

    public HologramData withAnimation(final AnimationType newAnimation) {
        return new HologramData(name, world, x, y, z, lines, visible, newAnimation);
    }

    public HologramData withVisibility(final boolean newVisibility) {
        return new HologramData(name, world, x, y, z, lines, newVisibility, animation);
    }
}
