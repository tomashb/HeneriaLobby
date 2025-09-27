package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendSettings;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.utils.HeadManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
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
    private static final int SAVE_SLOT = 40;
    private static final int RESET_SLOT = 42;
    private static final int BACK_SLOT = 45;

    private Inventory inventory;
    private FriendSettings settings;
    private final AtomicBoolean opened = new AtomicBoolean(false);
    private final HeadManager headManager;

    public FriendSettingsMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final FriendsMenuManager menuManager,
                              final Player player) {
        super(plugin, friendsManager, menuManager, player);
        this.headManager = plugin.getHeadManager();
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
        final ItemStack glass = createItem(Material.ORANGE_STAINED_GLASS_PANE, "§7");
        final int[] goldSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : goldSlots) {
            inventory.setItem(slot, glass);
        }
    }

    private void displayLoadingState() {
        final ItemStack loading = createItem(Material.CLOCK, "§eChargement des paramètres...", Arrays.asList(
                "§7Veuillez patienter quelques secondes",
                "§7pendant la récupération de vos",
                "§7préférences d'amitié."
        ));
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
        final String status = getSettingDisplay("notifications");
        return buildHeadItem("4120", "§e🔔 Notifications d'Amis", Arrays.asList(
                "§7Recevoir des alertes de vos amis",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Types:",
                "§8• §aConnexion/Déconnexion",
                "§8• §aMessages privés",
                "§8• §aInvitations de jeu",
                "",
                "§8» §eCliquez pour activer/désactiver"
        ), Material.BELL);
    }

    private ItemStack buildVisibilityItem() {
        final String status = getSettingDisplay("visibility");
        return buildHeadItem("3644", "§a🟢 Statut en Ligne", Arrays.asList(
                "§7Qui voit votre statut de connexion",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Options:",
                "§8• §aVisible - Tout le monde",
                "§8• §eAmis seulement",
                "§8• §6Favoris",
                "§8• §cInvisible",
                "",
                "§8» §eCliquez pour changer"
        ), Material.ENDER_EYE);
    }

    private ItemStack buildAutoRequestsItem() {
        final String status = getSettingDisplay("auto_requests");
        return buildHeadItem("5568", "§b📝 Demandes d'Amitié", Arrays.asList(
                "§7Qui peut vous inviter",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Options:",
                "§8• §aAcceptation automatique",
                "§8• §eAmis mutuels requis",
                "§8• §6Validation manuelle",
                "§8• §cRefus automatique",
                "",
                "§8» §eCliquez pour modifier"
        ), Material.PAPER);
    }

    private ItemStack buildSoundsItem() {
        final String status = getSettingDisplay("sounds");
        return buildHeadItem("3045", "§d🎵 Sons d'Amitié", Arrays.asList(
                "§7Personnaliser les sons d'alerte",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Options:",
                "§8• §aActivés",
                "§8• §cDésactivés",
                "",
                "§8» §eCliquez pour basculer"
        ), Material.NOTE_BLOCK);
    }

    private ItemStack buildPrivateMessagesItem() {
        final String status = getSettingDisplay("private_messages");
        return buildHeadItem("2177", "§d💬 Messages Privés", Arrays.asList(
                "§7Qui peut vous écrire",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Options:",
                "§8• §aTous",
                "§8• §eAmis",
                "§8• §6Favoris",
                "§8• §cDésactivé",
                "",
                "§8» §eCliquez pour ajuster"
        ), Material.WRITABLE_BOOK);
    }

    private ItemStack buildTeleportationItem() {
        final String status = getSettingDisplay("teleportation");
        return buildHeadItem("7129", "§5🌀 Téléportation", Arrays.asList(
                "§7Contrôler les téléportations",
                "",
                "§8▸ §aActuel: §f" + status,
                "",
                "§7Options:",
                "§8• §aLibre - Tout le monde",
                "§8• §eDemander permission",
                "§8• §6Favoris uniquement",
                "§8• §cDésactivé",
                "",
                "§8» §eCliquez pour modifier"
        ), Material.ENDER_PEARL);
    }

    private void setupNavigation() {
        final ItemStack save = buildHeadItem("4654", "§a💾 Sauvegarder", Arrays.asList(
                "§7Enregistrer tous les paramètres",
                "",
                "§8» §aCliquez pour sauvegarder"
        ), Material.EMERALD);
        inventory.setItem(SAVE_SLOT, save);

        final ItemStack reset = buildHeadItem("9056", "§c🔄 Réinitialiser", Arrays.asList(
                "§7Remettre les paramètres par défaut",
                "",
                "§c⚠ Action irréversible",
                "",
                "§8» §cCliquez pour réinitialiser"
        ), Material.REDSTONE);
        inventory.setItem(RESET_SLOT, reset);

        final ItemStack back = createItem(Material.ARROW, "§c« Retour aux Amis", Arrays.asList(
                "§7Revenir au menu principal",
                "",
                "§8» §cCliquez pour retourner"
        ));
        inventory.setItem(BACK_SLOT, back);
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
            case SAVE_SLOT -> handleSave();
            case RESET_SLOT -> handleReset();
            case BACK_SLOT -> handleBack();
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

    private void handleSave() {
        if (settings == null) {
            return;
        }
        friendsManager.saveFriendSettings(player, settings).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§a✓ Paramètres sauvegardés !");
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                    } else {
                        player.sendMessage("§cImpossible de sauvegarder pour le moment.");
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                    }
                })
        );
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
        return createItem(material, name, List.of());
    }

    private ItemStack createItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(new ArrayList<>(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildHeadItem(final String headId,
                                    final String name,
                                    final List<String> lore,
                                    final Material fallback) {
        if (headManager != null) {
            return headManager.createCustomHead(headId, name, lore);
        }
        return createItem(fallback, name, lore);
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
