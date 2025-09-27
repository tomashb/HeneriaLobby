package com.lobby.friends.menu.options;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Temporary placeholder for the friend options menu while the detailed menu is
 * being developed. Provides contextual feedback to the player.
 */
public class FriendOptionsMenu {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final FriendData friend;

    public FriendOptionsMenu(final LobbyPlugin plugin,
                             final FriendsManager friendsManager,
                             final Player player,
                             final FriendData friend) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.friend = friend;
    }

    public void open() {
        if (player == null || friend == null) {
            return;
        }
        player.sendMessage("§aOptions disponibles pour §e" + friend.getPlayerName() + "§a :");
        player.sendMessage("§7- §fTéléportation rapide (si autorisée)");
        player.sendMessage("§7- §fMessages privés et hors-ligne");
        player.sendMessage("§7- §fGestion des favoris et blocage");
        player.sendMessage("§e⚠ Menu détaillé en cours de développement");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }
}
