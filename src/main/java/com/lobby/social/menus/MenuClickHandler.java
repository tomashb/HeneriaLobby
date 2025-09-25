package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendRequest;
import com.lobby.social.clans.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuClickHandler implements Listener {

    private static final String CLAN_MEMBER_MANAGEMENT_PREFIX = "§8» §eGestion";

    private static final long CLICK_DELAY_TICKS = 10L;

    private final Set<UUID> clickCooldown = ConcurrentHashMap.newKeySet();

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
        if (event.getInventory().getHolder() instanceof Menu) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        if (clickCooldown.contains(playerId)) {
            event.setCancelled(true);
            return;
        }
        clickCooldown.add(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, (Runnable) () -> clickCooldown.remove(playerId), CLICK_DELAY_TICKS);

        final String title = event.getView().getTitle();
        if (!title.contains("»")) {
            return;
        }

        event.setCancelled(true);
        if (ClanMenus.CLAN_MEMBERS_TITLE.equals(title)) {
            handleClanMembersClick(event, player);
            return;
        }
        if (!Objects.equals(event.getView().getTopInventory(), event.getClickedInventory())) {
            return;
        }
        if (isFriendsMenuTitle(title)) {
            handleFriendsMenuClick(player, title, event.getSlot(), event.getClick());
            return;
        }
        if (isClanMenuTitle(title)) {
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
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (ClanMenus.CLAN_MEMBERS_TITLE.equals(title)
                || title.startsWith(CLAN_MEMBER_MANAGEMENT_PREFIX)
                || title.startsWith("§8» §cPermissions")) {
            if (ClanMenus.CLAN_MEMBERS_TITLE.equals(title)) {
                ClanMenus.clearMemberCache(player.getUniqueId());
            }
            if (placeholderManager != null) {
                placeholderManager.clearClanPermissionTarget(player.getUniqueId());
            }
            clickCooldown.remove(player.getUniqueId());
        }
    }

    private void handleFriendsMenuClick(final Player player, final String title, final int slot, final ClickType clickType) {
        if (slot < 0) {
            return;
        }
        if (slot == 49) {
            openMenu(player, "amis_menu");
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

    private void handleClanMembersClick(final InventoryClickEvent event, final Player player) {
        if (!Objects.equals(event.getView().getTopInventory(), event.getClickedInventory())) {
            return;
        }
        final int slot = event.getSlot();
        if (slot < 0) {
            return;
        }
        if (slot == 49) {
            openMenu(player, "clan_menu");
            return;
        }
        if (slot == ClanMenus.INVITE_SLOT) {
            ChatInputManager.startClanInviteFlow(player);
            return;
        }
        final var current = event.getCurrentItem();
        if (current == null || current.getType() != Material.PLAYER_HEAD) {
            return;
        }
        final UUID target = ClanMenus.getMemberAtSlot(player.getUniqueId(), slot);
        if (target == null) {
            return;
        }
        openClanMemberManagement(player, target);
    }

    private void handleClanMenuClick(final Player player, final String title, final int slot) {
        if (slot < 0) {
            return;
        }
        if (slot == 49) {
            openMenu(player, "clan_menu");
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
                SocialHeavyMenus.openClanBankMenu(menuManager, player);
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
            SocialHeavyMenus.openClanBankMenu(menuManager, player);
        }, () -> SocialHeavyMenus.openClanBankMenu(menuManager, player));
    }

    private void startWithdrawFlow(final Player player) {
        player.closeInventory();
        player.sendMessage("§e§l» Retirer des Coins");
        player.sendMessage("§7Tapez le montant à retirer:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        ChatInputManager.startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                SocialHeavyMenus.openClanBankMenu(menuManager, player);
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
            SocialHeavyMenus.openClanBankMenu(menuManager, player);
        }, () -> SocialHeavyMenus.openClanBankMenu(menuManager, player));
    }

    private void openMenu(final Player player, final String menuId) {
        if (menuManager != null) {
            menuManager.openMenu(player, menuId);
        }
    }

    private void openClanMemberManagement(final Player player, final UUID target) {
        if (menuManager == null) {
            return;
        }
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (placeholderManager != null) {
            placeholderManager.setClanPermissionTarget(player.getUniqueId(), target);
        }
        menuManager.openMenu(player, "clan_member_management_menu");
    }

    private boolean isFriendsMenuTitle(final String title) {
        return FriendsMenus.FRIENDS_ONLINE_TITLE.equals(title) || FriendsMenus.FRIEND_REQUESTS_TITLE.equals(title);
    }

    private boolean isClanMenuTitle(final String title) {
        return ClanMenus.CLAN_MEMBERS_TITLE.equals(title)
                || ClanMenus.CLAN_VAULT_TITLE.equals(title)
                || title.startsWith(CLAN_MEMBER_MANAGEMENT_PREFIX);
    }
}
