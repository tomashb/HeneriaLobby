package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.menus.MenuRenderContext;
import com.lobby.social.ChatInputManager;
import com.lobby.social.groups.Group;
import com.lobby.social.groups.GroupManager;
import com.lobby.social.groups.GroupSettings;
import com.lobby.social.groups.GroupVisibility;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Displays the current party / group state. Offers a recruitment view for
 * players without a group and a management dashboard otherwise.
 */
public class GroupMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &6Mon Groupe");
    private static final int SIZE = 54;
    private static final List<Integer> MEMBER_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final GroupManager groupManager;
    private final Group group;
    private final int pendingInvites;
    private final UUID viewerUuid;
    private final Map<Integer, UUID> memberSlots = new HashMap<>();

    private Inventory inventory;

    public GroupMenu(final LobbyPlugin plugin,
                     final MenuManager menuManager,
                     final AssetManager assetManager,
                     final GroupManager groupManager,
                     final Group group,
                     final int pendingInvites,
                     final UUID viewerUuid) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.groupManager = groupManager;
        this.group = group;
        this.pendingInvites = Math.max(0, pendingInvites);
        this.viewerUuid = viewerUuid;
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        memberSlots.clear();

        fillBackground();
        placeBorders();
        if (group == null) {
            placeRecruitmentView(player);
        } else {
            placeGroupView(player);
        }
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (group == null) {
            handleRecruitmentClick(player, slot);
            return;
        }
        if (slot == 3 && group.isLeader(viewerUuid)) {
            ChatInputManager.startGroupInviteFlow(player);
            return;
        }
        if (slot == 4 && group.isLeader(viewerUuid)) {
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%party_status%", formatGroupVisibility(groupManager.getGroupSettings(viewerUuid)));
            final MenuRenderContext context = MenuRenderContext.EMPTY.withGroup(true, true);
            if (!menuManager.openMenu(player, "party_management_menu", placeholders, context)) {
                player.sendMessage("§cOutil de gestion indisponible pour le moment.");
            }
            return;
        }
        if (slot == 5 && group.isLeader(viewerUuid)) {
            executeAsyncAndRefresh(player, () -> groupManager.disbandGroup(player));
            return;
        }
        if (slot == 7) {
            player.closeInventory();
            player.performCommand("party chat");
            return;
        }
        if (slot == 8) {
            executeAsyncAndRefresh(player, () -> groupManager.leaveGroup(player));
            return;
        }
        if (slot == 49) {
            menuManager.openMenu(player, "profil_menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void handleRecruitmentClick(final Player player, final int slot) {
        if (slot == 13) {
            ChatInputManager.startGroupCreateFlow(player);
            return;
        }
        if (slot == 31) {
            if (!menuManager.openMenu(player, "party_invites_menu")) {
                player.sendMessage("§cVous n'avez aucune invitation en attente.");
            }
            return;
        }
        if (slot == 49) {
            menuManager.openMenu(player, "profil_menu");
        }
    }

    private void executeAsyncAndRefresh(final Player player, final Runnable task) {
        if (plugin == null) {
            task.run();
            SocialHeavyMenus.openGroupMenu(menuManager, player);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            task.run();
            Bukkit.getScheduler().runTask(plugin, () -> SocialHeavyMenus.openGroupMenu(menuManager, player));
        });
    }

    private void placeRecruitmentView(final Player player) {
        inventory.setItem(13, decorate(assetManager.getHead("hdb:9723"), "§a§lCréer un Groupe",
                List.of(
                        "§7Fondez votre propre groupe",
                        "§7et invitez vos amis à jouer !",
                        "§r",
                        "§a▶ Cliquez pour créer"
                )));

        final ItemStack invites = new ItemStack(Material.WRITABLE_BOOK);
        final ItemMeta invitesMeta = invites.getItemMeta();
        if (invitesMeta != null) {
            invitesMeta.setDisplayName("§b§lInvitations de Groupe (§6" + pendingInvites + "§b)");
            invitesMeta.setLore(List.of(
                    "§7Consultez et acceptez vos",
                    "§7invitations en attente.",
                    "§r",
                    "§a▶ Cliquez pour ouvrir"
            ));
            invites.setItemMeta(invitesMeta);
        }
        inventory.setItem(31, invites);

        inventory.setItem(49, decorate(assetManager.getHead("hdb:9334"), "§cRetour", List.of("§7Revenir au profil")));
    }

    private void placeGroupView(final Player player) {
        final boolean isLeader = group.isLeader(viewerUuid);

        if (isLeader) {
            inventory.setItem(3, decorate(assetManager.getHead("hdb:47365"), "§a§lInviter un Joueur",
                    List.of("§7Envoyez une invitation à un ami")));
            inventory.setItem(4, decorate(new ItemStack(Material.ANVIL), "§6§lGérer le Groupe",
                    List.of("§7Ouvrir le panneau de gestion")));
            inventory.setItem(5, decorate(assetManager.getHead("hdb:47366"), "§c§lDissoudre le Groupe",
                    List.of("§7Dissout immédiatement le groupe")));
        }

        inventory.setItem(7, decorate(new ItemStack(Material.OAK_SIGN), "§e§lChat de Groupe",
                List.of("§7Basculer vers le chat de groupe")));
        inventory.setItem(8, decorate(assetManager.getHead("hdb:31408"), "§c§lQuitter le Groupe",
                List.of("§7Quittez votre groupe actuel")));

        inventory.setItem(49, decorate(assetManager.getHead("hdb:9334"), "§cRetour", List.of("§7Revenir au profil")));

        final List<UUID> members = new ArrayList<>(group.getMembers());
        for (int index = 0; index < members.size() && index < MEMBER_SLOTS.size(); index++) {
            final UUID uuid = members.get(index);
            final int slot = MEMBER_SLOTS.get(index);
            inventory.setItem(slot, createMemberItem(uuid));
            memberSlots.put(slot, uuid);
        }

        final ItemStack info = new ItemStack(Material.COMPASS);
        final ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6§l" + group.getDisplayName());
            infoMeta.setLore(List.of(
                    "§7Membres: §e" + group.getSize() + "§7/§e" + group.getMaxSize(),
                    "§7Chef: §e" + resolveName(group.getLeaderUUID()),
                    "§7Créé le: §e" + formatDate(group.getCreatedAt())
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(22, info);
    }

    private String formatGroupVisibility(final GroupSettings settings) {
        final GroupVisibility visibility = settings == null ? GroupVisibility.PUBLIC : settings.getVisibility();
        return switch (visibility) {
            case PUBLIC -> "§aPublic";
            case FRIENDS_ONLY -> "§eAmis uniquement";
            case INVITE_ONLY -> "§cSur invitation";
        };
    }

    private ItemStack createMemberItem(final UUID uuid) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(offlinePlayer);
            final Player online = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
            final boolean isLeader = group.isLeader(uuid);
            final boolean isModerator = group.isModerator(uuid);
            final String color = online != null ? "§a" : "§7";
            meta.setDisplayName(color + offlinePlayer.getName());
            final List<String> lore = new ArrayList<>();
            lore.add("§7Rôle: " + formatRole(isLeader, isModerator));
            lore.add("§7Statut: " + (online != null ? "§aEn ligne" : "§cHors ligne"));
            lore.add("§7Depuis: §e" + formatDate(group.getCreatedAt()));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private String formatRole(final boolean leader, final boolean moderator) {
        if (leader) {
            return "§6Chef";
        }
        if (moderator) {
            return "§eModérateur";
        }
        return "§7Membre";
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void placeBorders() {
        final ItemStack border = createGlass(Material.ORANGE_STAINED_GLASS_PANE);
        final int[] slots = {
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 17, 18, 26, 27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44,
                45, 46, 47, 48, 49, 50, 51, 52, 53
        };
        for (int slot : slots) {
            inventory.setItem(slot, border);
        }
    }

    private ItemStack decorate(final ItemStack base, final String name, final List<String> lore) {
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            base.setItemMeta(meta);
        }
        return base;
    }

    private ItemStack createGlass(final Material material) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private String resolveName(final UUID uuid) {
        if (uuid == null) {
            return "Inconnu";
        }
        final Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    private String formatDate(final long epochMillis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
