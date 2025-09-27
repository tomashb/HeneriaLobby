package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Ensures that all friends related configuration files are present inside the
 * plugin data folder. Missing files are copied from the plugin resources so
 * that server owners can customise the menus without manual extraction.
 */
public final class FriendsConfigGenerator {

    private static final String[] RESOURCES = {
            "friends/friends_main_menu.yml",
            "friends/friends_statistics_menu.yml",
            "friends/friends_settings_menu.yml",
            "friends/friends_add_menu.yml",
            "friends/friends_list.yml",
            "friends/friend_options.yml",
            "friends/friend_requests.yml",
            "friends/nearby_players.yml",
            "friends/search_results.yml",
            "friends/suggestions.yml",
            "friends/notifications.yml",
            "friends/send_request.yml"
    };

    private final LobbyPlugin plugin;

    public FriendsConfigGenerator(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void generate() {
        final File friendsDirectory = new File(plugin.getDataFolder(), "friends");
        if (!friendsDirectory.exists() && !friendsDirectory.mkdirs()) {
            final Logger logger = plugin.getLogger();
            logger.warning("Impossible de créer le dossier des amis: " + friendsDirectory.getAbsolutePath());
            return;
        }

        for (String resource : RESOURCES) {
            saveIfMissing(resource);
        }
    }

    private void saveIfMissing(final String resourcePath) {
        final String fileName = Path.of(resourcePath).getFileName().toString();
        final File target = new File(plugin.getDataFolder(), "friends" + File.separator + fileName);
        if (target.exists()) {
            return;
        }
        try {
            plugin.saveResource(resourcePath, false);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("Ressource introuvable dans le jar: " + resourcePath);
        }
    }
}
