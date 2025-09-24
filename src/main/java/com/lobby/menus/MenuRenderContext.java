package com.lobby.menus;

/**
 * Represents runtime information used when rendering a configured menu.
 * Menus can query these flags to decide whether an item should be displayed
 * for the current player. The context is immutable and can be freely shared
 * between menu instances.
 */
public record MenuRenderContext(boolean hasGroup,
                                boolean groupLeader,
                                boolean hasClan,
                                boolean clanLeader) {

    public static final MenuRenderContext EMPTY = new MenuRenderContext(false, false, false, false);

    public MenuRenderContext withGroup(final boolean present, final boolean leader) {
        return new MenuRenderContext(present, leader, hasClan, clanLeader);
    }

    public MenuRenderContext withClan(final boolean present, final boolean leader) {
        return new MenuRenderContext(hasGroup, groupLeader, present, leader);
    }
}
