package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.clans.ClanMember;
import com.lobby.social.clans.ClanPermission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanMenus {

    public static final String CLAN_MEMBERS_TITLE = "§8» §eMembres du Clan";
    public static final String CLAN_VAULT_TITLE = "§8» §6Trésor du Clan";
    public static final int INVITE_SLOT = 46;
    public static final int DEPOSIT_SLOT = 20;
    public static final int WITHDRAW_SLOT = 24;

    private static final Map<UUID, Map<Integer, UUID>> MEMBER_SLOT_CACHE = new ConcurrentHashMap<>();

    private ClanMenus() {
    }

    public static void openClanMembersMenu(final Player player) {
        if (player == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        final ClanManager clanManager = plugin.getClanManager();
        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVous n'êtes dans aucun clan!");
            return;
        }

        final Inventory menu = Bukkit.createInventory(null, 54, CLAN_MEMBERS_TITLE);
        setupRedBorders(menu);

        final List<ClanMember> members = clanManager.getClanMembers(clan.getId());
        final Map<Integer, UUID> slotMap = new HashMap<>();
        int slot = 10;
        for (ClanMember member : members) {
            if (slot >= 44) {
                break;
            }
            final ItemStack item = createClanMemberItem(member);
            menu.setItem(slot, item);
            slotMap.put(slot, member.getUuid());
            slot = nextContentSlot(slot);
        }

        MEMBER_SLOT_CACHE.put(player.getUniqueId(), slotMap);

        if (clanManager.hasPermission(clan.getId(), player.getUniqueId(), "clan.invite")) {
            menu.setItem(INVITE_SLOT, createInviteItem());
        }

        addBackButton(menu, 49);
        player.openInventory(menu);
    }

    public static void openClanVaultMenu(final Player player) {
        if (player == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        final ClanManager clanManager = plugin.getClanManager();
        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVous n'êtes dans aucun clan!");
            return;
        }

        final Inventory menu = Bukkit.createInventory(null, 54, CLAN_VAULT_TITLE);
        setupRedBorders(menu);

        final ItemStack vaultInfo = new ItemStack(Material.CHEST);
        final ItemMeta infoMeta = vaultInfo.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6§lTrésor du Clan");
            infoMeta.setLore(Arrays.asList(
                    "§7Coins actuels: §e" + clan.getBankCoins(),
                    "§7Niveau du clan: §b" + clan.getLevel()
            ));
            vaultInfo.setItemMeta(infoMeta);
        }
        menu.setItem(13, vaultInfo);

        final ItemStack depositItem = new ItemStack(Material.GOLD_INGOT);
        final ItemMeta depositMeta = depositItem.getItemMeta();
        if (depositMeta != null) {
            depositMeta.setDisplayName("§a§lDéposer des Coins");
            depositMeta.setLore(Arrays.asList(
                    "§7Déposez vos coins dans le trésor",
                    "§r",
                    "§a▶ Cliquez pour déposer!"
            ));
            depositItem.setItemMeta(depositMeta);
        }
        menu.setItem(DEPOSIT_SLOT, depositItem);

        if (clanManager.hasPermission(clan.getId(), player.getUniqueId(), "clan.withdraw")) {
            final ItemStack withdrawItem = new ItemStack(Material.DIAMOND);
            final ItemMeta withdrawMeta = withdrawItem.getItemMeta();
            if (withdrawMeta != null) {
                withdrawMeta.setDisplayName("§c§lRetirer des Coins");
                withdrawMeta.setLore(Arrays.asList(
                        "§7Retirez des coins du trésor",
                        "§r",
                        "§c▶ Cliquez pour retirer!"
                ));
                withdrawItem.setItemMeta(withdrawMeta);
            }
            menu.setItem(WITHDRAW_SLOT, withdrawItem);
        }

        addBackButton(menu, 49);
        player.openInventory(menu);
    }

    public static void openMemberPermissionsMenu(final Player player, final UUID memberUuid) {
        if (player == null || memberUuid == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final MenuManager menuManager = plugin.getMenuManager();
        if (clanManager == null || menuManager == null) {
            return;
        }
        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVous n'êtes dans aucun clan!");
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.MANAGE_RANKS)
                && !clan.isLeader(player.getUniqueId())) {
            player.sendMessage("§cVous n'avez pas la permission de gérer les rangs.");
            return;
        }
        if (clan.isLeader(memberUuid)) {
            player.sendMessage("§cVous ne pouvez pas modifier les permissions du chef de clan.");
            return;
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member == null) {
            player.sendMessage("§cMembre introuvable.");
            return;
        }
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (placeholderManager != null) {
            placeholderManager.setClanPermissionTarget(player.getUniqueId(), memberUuid);
        }
        menuManager.openMenu(player, "clan_member_permissions_menu");
    }

    public static UUID getMemberAtSlot(final UUID viewerUuid, final int slot) {
        if (viewerUuid == null || slot < 0) {
            return null;
        }
        final Map<Integer, UUID> mapping = MEMBER_SLOT_CACHE.get(viewerUuid);
        if (mapping == null) {
            return null;
        }
        return mapping.get(slot);
    }

    public static void clearMemberCache(final UUID viewerUuid) {
        if (viewerUuid != null) {
            MEMBER_SLOT_CACHE.remove(viewerUuid);
        }
    }

    private static ItemStack createClanMemberItem(final ClanMember member) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
        final String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : member.getUuid().toString();
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName("§e" + name);
            meta.setLore(Arrays.asList(
                    "§7Rang: §f" + member.getRankName(),
                    "§7Contributions: §b" + member.getTotalContributions(),
                    "§r",
                    "§8▶ §7Cliquez pour gérer les permissions"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInviteItem() {
        final ItemStack inviteItem = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = inviteItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lInviter un Joueur");
            meta.setLore(Arrays.asList(
                    "§7Invitez un nouveau membre",
                    "§r",
                    "§a▶ Cliquez pour inviter!"
            ));
            inviteItem.setItemMeta(meta);
        }
        return inviteItem;
    }

    private static void addBackButton(final Inventory menu, final int slot) {
        final ItemStack back = new ItemStack(Material.ARROW);
        final ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lRetour");
            meta.setLore(Arrays.asList(
                    "§7Retourner au menu précédent"
            ));
            back.setItemMeta(meta);
        }
        menu.setItem(slot, back);
    }

    private static void setupRedBorders(final Inventory inventory) {
        final int[] borderSlots = {0, 1, 2, 6, 7, 8, 9, 17, 45, 53};
        final ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            pane.setItemMeta(meta);
        }
        for (int slot : borderSlots) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static int nextContentSlot(final int currentSlot) {
        int slot = currentSlot + 1;
        if ((slot + 1) % 9 == 0) {
            slot += 2;
        }
        return slot;
    }
}
