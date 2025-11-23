package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;

public abstract class Manager {
    protected final HeneriaLobby plugin;

    public Manager(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    public abstract void onEnable();
    public abstract void onDisable();
}
