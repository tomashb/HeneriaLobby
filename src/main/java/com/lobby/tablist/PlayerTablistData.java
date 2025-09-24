package com.lobby.tablist;

/**
 * Immutable container representing the LuckPerms related data used to render the
 * tablist for a player.
 */
record PlayerTablistData(String prefix, String suffix, int weight) {

    static PlayerTablistData empty() {
        return new PlayerTablistData("", "", 0);
    }
}
