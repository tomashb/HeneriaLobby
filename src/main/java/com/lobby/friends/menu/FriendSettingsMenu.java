package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendSettings;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive menu allowing players to manage their friendship preferences. The
 * menu synchronises changes with the {@link FriendsManager} and persists them
 * asynchronously through the {@link com.lobby.friends.database.FriendsDatabase}.
 */
public class FriendSettingsMenu extends BaseFriendsMenu {

    private static final String INVENTORY_TITLE = "§8» §6Paramètres d'Amitié";
    private static final int INVENTORY_SIZE = 54;

    private Inventory inventory;
    private FriendSettings settings;
    private final AtomicBoolean opened = new AtomicBoolean(false);

    public FriendSettingsMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final FriendsMenuManager menuManager,
                              final Player player) {
        super(plugin, friendsManager, menuManager, player);
    }

    @Override
    protected void openMenu() {
        loadSettings();
    }

    private void loadSettings() {
        friendsManager.getFriendSettings(player).thenAccept(retrieved -> {
            final FriendSettings resolved = retrieved != null
                    ? retrieved
                    : FriendSettings.defaults(player.getUniqueId().toString());
            this.settings = resolved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (inventory == null) {
                    inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
                }
                setupMenu();
                if (opened.compareAndSet(false, true)) {
                    final Player viewer = getPlayer();
                    if (viewer != null && viewer.isOnline()) {
                        viewer.openInventory(inventory);
                        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                } else {
                    final Player viewer = getPlayer();
                    if (viewer != null) {
                        viewer.updateInventory();
                    }
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Impossible de charger les paramètres d'amis : " + throwable.getMessage());
            this.settings = FriendSettings.defaults(player.getUniqueId().toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (inventory == null) {
                    inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
                }
                setupMenu();
                if (opened.compareAndSet(false, true)) {
                    final Player viewer = getPlayer();
                    if (viewer != null && viewer.isOnline()) {
                        viewer.openInventory(inventory);
                        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                } else {
                    final Player viewer = getPlayer();
                    if (viewer != null) {
                        viewer.updateInventory();
                    }
                }
            });
            return null;
        });
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        fillFrame();
        if (settings == null) {
            displayLoadingState();
            return;
        }
        setupMainSettings();
        setupNavigation();
    }

    private void fillFrame() {
        final ItemStack glass = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        final int[] goldSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : goldSlots) {
            inventory.setItem(slot, glass);
        }
    }

    private void displayLoadingState() {
        final ItemStack loading = createItem(Material.CLOCK, "§eChargement des paramètres...");
        final ItemMeta meta = loading.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "§7Veuillez patienter quelques secondes",
                    "§7pendant la récupération de vos",
                    "§7préférences d'amitié."
            ));
            loading.setItemMeta(meta);
        }
        inventory.setItem(22, loading);
    }

    private void setupMainSettings() {
        inventory.setItem(10, buildNotificationsItem());
        inventory.setItem(11, buildVisibilityItem());
        inventory.setItem(12, buildAutoRequestsItem());
        inventory.setItem(13, buildSoundsItem());
        inventory.setItem(14, buildPrivateMessagesItem());
        inventory.setItem(15, buildTeleportationItem());
    }

    private ItemStack buildNotificationsItem() {
        final ItemStack item = createItem(Material.BELL, "§6§l🔔 Notifications");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("notifications");
            meta.setLore(Arrays.asList(
                    "§7Configurez vos notifications d'amis",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Options disponibles:",
                    "§8▸ §aToutes §7- Toutes les notifications",
                    "§8▸ §eImportantes §7- Connexions et messages",
                    "§8▸ §6Favoris §7- Favoris uniquement",
                    "§8▸ §cAucune §7- Aucune notification",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildVisibilityItem() {
        final ItemStack item = createItem(Material.ENDER_EYE, "§6§l👁️ Visibilité en Ligne");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("visibility");
            meta.setLore(Arrays.asList(
                    "§7Contrôlez qui peut voir votre statut",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Options:",
                    "§8▸ §aPublic §7- Visible par tous",
                    "§8▸ §eAmis §7- Visible par vos amis",
                    "§8▸ §6Favoris §7- Favoris uniquement",
                    "§8▸ §cInvisible §7- Toujours hors-ligne",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildAutoRequestsItem() {
        final ItemStack item = createItem(Material.HOPPER, "§6§l📨 Demandes Automatiques");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("auto_requests");
            meta.setLore(Arrays.asList(
                    "§7Gestion automatique des demandes",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Options:",
                    "§8▸ §aAccepter auto §7- Accepter toutes",
                    "§8▸ §eAmis mutuels §7- Auto si 3+ amis communs",
                    "§8▸ §6Manuel §7- Décider manuellement",
                    "§8▸ §cRefuser auto §7- Refuser toutes",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildSoundsItem() {
        final ItemStack item = createItem(Material.NOTE_BLOCK, "§6§l🎵 Sons d'Amitié");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("sounds");
            meta.setLore(Arrays.asList(
                    "§7Contrôlez les sons d'interactions",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Types de sons:",
                    "§8▸ §7Connexion/Déconnexion amis",
                    "§8▸ §7Messages privés reçus",
                    "§8▸ §7Demandes d'amitié",
                    "",
                    "§8» §6Cliquez pour activer/désactiver"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildPrivateMessagesItem() {
        final ItemStack item = createItem(Material.WRITABLE_BOOK, "§6§l💬 Messages Privés");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("private_messages");
            meta.setLore(Arrays.asList(
                    "§7Contrôlez qui peut vous envoyer des MP",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Options:",
                    "§8▸ §aTous §7- Tous les joueurs",
                    "§8▸ §eAmis §7- Vos amis uniquement",
                    "§8▸ §6Favoris §7- Favoris uniquement",
                    "§8▸ §cDésactivé §7- Aucun message",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildTeleportationItem() {
        final ItemStack item = createItem(Material.ENDER_PEARL, "§6§l🚀 Téléportation");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String status = getSettingDisplay("teleportation");
            meta.setLore(Arrays.asList(
                    "§7Paramètres de téléportation vers vous",
                    "",
                    "§6▸ État actuel: §e" + status,
                    "",
                    "§7Options:",
                    "§8▸ §aLibre §7- Téléportation instantanée",
                    "§8▸ §eDemander §7- Demander permission",
                    "§8▸ §6Favoris §7- Favoris sans permission",
                    "§8▸ §cDésactivé §7- Aucune téléportation",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setupNavigation() {
        final ItemStack reset = createItem(Material.TNT, "§c🔄 Réinitialiser");
        final ItemMeta resetMeta = reset.getItemMeta();
        if (resetMeta != null) {
            resetMeta.setLore(Arrays.asList(
                    "§cRéinitialiser tous les paramètres",
                    "§caux valeurs par défaut",
                    "",
                    "§c⚠ Cette action est irréversible !",
                    "",
                    "§8» §cCliquez pour réinitialiser"
            ));
            reset.setItemMeta(resetMeta);
        }
        inventory.setItem(48, reset);

        final ItemStack back = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal des amis",
                    "",
                    "§e▸ Modifications sauvegardées automatiquement",
                    "",
                    "§8» §eCliquez pour retourner"
            ));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(49, back);
    }

    private String getSettingDisplay(final String setting) {
        final String raw = getRawSettingValue(setting);
        return switch (setting) {
            case "notifications" -> switch (raw) {
                case "ALL" -> "Toutes";
                case "IMPORTANT" -> "Importantes";
                case "FAVORITES" -> "Favoris";
                case "NONE" -> "Aucune";
                default -> "Importantes";
            };
            case "visibility" -> switch (raw) {
                case "PUBLIC" -> "Public";
                case "FRIENDS" -> "Amis";
                case "FAVORITES" -> "Favoris";
                case "INVISIBLE" -> "Invisible";
                default -> "Amis";
            };
            case "auto_requests" -> switch (raw) {
                case "ACCEPT" -> "Accepter auto";
                case "MUTUAL" -> "Amis mutuels";
                case "MANUAL" -> "Manuel";
                case "REJECT" -> "Refuser auto";
                default -> "Manuel";
            };
            case "sounds" -> "ENABLED".equals(raw) ? "Activés" : "Désactivés";
            case "private_messages" -> switch (raw) {
                case "ALL" -> "Tous";
                case "FRIENDS" -> "Amis";
                case "FAVORITES" -> "Favoris";
                case "DISABLED" -> "Désactivé";
                default -> "Amis";
            };
            case "teleportation" -> switch (raw) {
                case "FREE" -> "Libre";
                case "ASK_PERMISSION" -> "Demander";
                case "FAVORITES" -> "Favoris";
                case "DISABLED" -> "Désactivé";
                default -> "Demander";
            };
            default -> raw;
        };
    }

    private String getRawSettingValue(final String setting) {
        if (settings == null) {
            return "";
        }
        return switch (setting) {
            case "notifications" -> settings.getNotifications();
            case "visibility" -> settings.getVisibility();
            case "auto_requests" -> settings.getAutoRequests();
            case "sounds" -> settings.isSoundsEnabled() ? "ENABLED" : "DISABLED";
            case "private_messages" -> settings.getPrivateMessages();
            case "teleportation" -> settings.getTeleportation();
            default -> "";
        };
    }

    @Override
    public void handleMenuClick(final InventoryClickEvent event) {
        if (!INVENTORY_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        final Player clicker = getPlayer();
        if (clicker == null) {
            return;
        }
        final int slot = event.getSlot();
        switch (slot) {
            case 10 -> cycleSetting("notifications", Arrays.asList("ALL", "IMPORTANT", "FAVORITES", "NONE"));
            case 11 -> cycleSetting("visibility", Arrays.asList("PUBLIC", "FRIENDS", "FAVORITES", "INVISIBLE"));
            case 12 -> cycleSetting("auto_requests", Arrays.asList("ACCEPT", "MUTUAL", "MANUAL", "REJECT"));
            case 13 -> cycleSetting("sounds", Arrays.asList("ENABLED", "DISABLED"));
            case 14 -> cycleSetting("private_messages", Arrays.asList("ALL", "FRIENDS", "FAVORITES", "DISABLED"));
            case 15 -> cycleSetting("teleportation", Arrays.asList("FREE", "ASK_PERMISSION", "FAVORITES", "DISABLED"));
            case 48 -> handleReset();
            case 49 -> handleBack();
            default -> {
            }
        }
    }

    private void cycleSetting(final String setting, final List<String> values) {
        if (settings == null || values.isEmpty()) {
            return;
        }
        final String current = getRawSettingValue(setting);
        final int currentIndex = values.indexOf(current);
        final int nextIndex = (currentIndex + 1) % values.size();
        final String newValue = values.get(nextIndex);
        final FriendSettings updated = applySetting(settings, setting, newValue);
        if (updated == null) {
            return;
        }
        friendsManager.saveFriendSettings(player, updated).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    player.sendMessage("§cImpossible de mettre à jour ce paramètre pour le moment.");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                    return;
                }
                settings = updated;
                setupMenu();
                player.updateInventory();
                final String displayValue = getSettingDisplay(setting);
                player.sendMessage("§a✓ Paramètre modifié: §e" + formatSettingName(setting) + " §8→ §a" + displayValue);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            });
        });
    }

    private FriendSettings applySetting(final FriendSettings base, final String setting, final String newValue) {
        return switch (setting) {
            case "notifications" -> base.withNotifications(newValue);
            case "visibility" -> base.withVisibility(newValue);
            case "auto_requests" -> base.withAutoRequests(newValue);
            case "sounds" -> base.withSoundsEnabled("ENABLED".equalsIgnoreCase(newValue));
            case "private_messages" -> base.withPrivateMessages(newValue);
            case "teleportation" -> base.withTeleportation(newValue);
            default -> null;
        };
    }

    private void handleReset() {
        friendsManager.resetFriendSettings(player).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    player.sendMessage("§cImpossible de réinitialiser vos paramètres maintenant.");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                    return;
                }
                settings = FriendSettings.defaults(player.getUniqueId().toString());
                setupMenu();
                player.updateInventory();
                player.sendMessage("§a✓ Paramètres réinitialisés aux valeurs par défaut !");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            });
        });
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> new FriendsMainMenu(plugin, friendsManager, menuManager, player).open(), 3L);
    }

    @Override
    public void handleMenuClose(final InventoryCloseEvent event) {
        if (!INVENTORY_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        opened.set(false);
        super.handleMenuClose(event);
    }

    private ItemStack createItem(final Material material, final String name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String getTitle() {
        return INVENTORY_TITLE;
    }

    private String formatSettingName(final String setting) {
        return switch (setting) {
            case "notifications" -> "Notifications";
            case "visibility" -> "Visibilité";
            case "auto_requests" -> "Demandes auto";
            case "sounds" -> "Sons";
            case "private_messages" -> "Messages privés";
            case "teleportation" -> "Téléportation";
            default -> setting;
        };
    }
}
