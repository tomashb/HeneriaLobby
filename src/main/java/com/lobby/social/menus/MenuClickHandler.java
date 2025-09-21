package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class MenuClickHandler implements Listener {

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final FriendManager friendManager;
    private final ClanManager clanManager;

    public MenuClickHandler(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.menuManager = plugin.getMenuManager();
        this.friendManager = plugin.getFriendManager();
        this.clanManager = plugin.getClanManager();
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Inventory inventory = event.getView().getTopInventory();
        if (!Objects.equals(inventory, event.getClickedInventory())) {
            return;
        }
        final String title = event.getView().getTitle();
        if (isFriendsMenuTitle(title)) {
            event.setCancelled(true);
            handleFriendsMenuClick(player, title, event.getSlot(), event.getClick());
            return;
        }
        if (isClanMenuTitle(title)) {
            event.setCancelled(true);
            handleClanMenuClick(player, title, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final String title = event.getView().getTitle();
        if (isFriendsMenuTitle(title)) {
            FriendsMenus.clearRequestCache(player.getUniqueId());
        }
    }

    private void handleFriendsMenuClick(final Player player, final String title, final int slot, final ClickType clickType) {
        if (slot < 0) {
            return;
        }
        if (slot == 49) {
            openMenu(player, "friends_menu");
            return;
        }
        if (FriendsMenus.FRIENDS_ONLINE_TITLE.equals(title) && slot == 46) {
            friendManager.refreshOnlineFriends(player.getUniqueId());
            FriendsMenus.openFriendsOnlineMenu(player);
            return;
        }
        if (FriendsMenus.FRIEND_REQUESTS_TITLE.equals(title)) {
            final FriendRequest request = FriendsMenus.getRequestAt(player, slot);
            if (request == null) {
                return;
            }
            if (clickType == ClickType.RIGHT) {
                friendManager.denyFriendRequest(player, request.getSender());
            } else {
                friendManager.acceptFriendRequest(player, request.getSender());
            }
            FriendsMenus.openFriendRequestsMenu(player);
        }
    }

    private void handleClanMenuClick(final Player player, final String title, final int slot) {
        if (slot < 0) {
            return;
        }
        if (slot == 49) {
            openMenu(player, "clan_menu");
            return;
        }
        if (ClanMenus.CLAN_MEMBERS_TITLE.equals(title)) {
            if (slot == ClanMenus.INVITE_SLOT) {
                ChatInputManager.startClanInviteFlow(player);
            }
            return;
        }
        if (ClanMenus.CLAN_VAULT_TITLE.equals(title)) {
            if (slot == ClanMenus.DEPOSIT_SLOT) {
                startDepositFlow(player);
            } else if (slot == ClanMenus.WITHDRAW_SLOT) {
                startWithdrawFlow(player);
            }
        }
    }

    private void startDepositFlow(final Player player) {
        player.closeInventory();
        player.sendMessage("§e§l» Déposer des Coins");
        player.sendMessage("§7Tapez le montant à déposer:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        ChatInputManager.startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                ClanMenus.openClanVaultMenu(player);
                return;
            }
            try {
                final long amount = Long.parseLong(input);
                if (amount <= 0) {
                    player.sendMessage("§cMontant invalide!");
                } else if (clanManager.depositCoins(player, amount)) {
                    player.sendMessage("§aDépôt effectué!");
                } else {
                    player.sendMessage("§cErreur: Fonds insuffisants");
                }
            } catch (NumberFormatException exception) {
                player.sendMessage("§cMontant invalide!");
            }
            ClanMenus.openClanVaultMenu(player);
        }, () -> ClanMenus.openClanVaultMenu(player));
    }

    private void startWithdrawFlow(final Player player) {
        player.closeInventory();
        player.sendMessage("§e§l» Retirer des Coins");
        player.sendMessage("§7Tapez le montant à retirer:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        ChatInputManager.startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                ClanMenus.openClanVaultMenu(player);
                return;
            }
            try {
                final long amount = Long.parseLong(input);
                if (amount <= 0) {
                    player.sendMessage("§cMontant invalide!");
                } else if (clanManager.withdrawCoins(player, amount)) {
                    player.sendMessage("§aRetrait effectué!");
                } else {
                    player.sendMessage("§cImpossible de retirer ce montant");
                }
            } catch (NumberFormatException exception) {
                player.sendMessage("§cMontant invalide!");
            }
            ClanMenus.openClanVaultMenu(player);
        }, () -> ClanMenus.openClanVaultMenu(player));
    }

    private void openMenu(final Player player, final String menuId) {
        if (menuManager != null) {
            menuManager.openMenu(player, menuId);
        }
    }

    private boolean isFriendsMenuTitle(final String title) {
        return FriendsMenus.FRIENDS_ONLINE_TITLE.equals(title) || FriendsMenus.FRIEND_REQUESTS_TITLE.equals(title);
    }

    private boolean isClanMenuTitle(final String title) {
        return ClanMenus.CLAN_MEMBERS_TITLE.equals(title) || ClanMenus.CLAN_VAULT_TITLE.equals(title);
    }
}
