package com.lobby.social;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.groups.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputManager implements Listener {

    private static ChatInputManager instance;

    private final LobbyPlugin plugin;
    private final Map<UUID, InputSession> waitingInputs = new ConcurrentHashMap<>();

    public ChatInputManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static ChatInputManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ChatInputManager has not been initialized");
        }
        return instance;
    }

    public static void startFriendAddFlow(final Player player) {
        if (player == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        player.closeInventory();
        player.sendMessage("§e§l» Ajouter un ami");
        player.sendMessage("§7Tapez le nom du joueur à ajouter:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        final MenuManager menuManager = manager.plugin.getMenuManager();
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cAjout annulé");
                if (menuManager != null) {
                    menuManager.openMenu(player, "amis_menu");
                }
                return;
            }
            if (manager.plugin.getFriendManager().sendFriendRequest(player, input)) {
                player.sendMessage("§aDemande envoyée à §6" + input + "§a !");
            } else {
                player.sendMessage("§cImpossible d'envoyer la demande");
            }
            if (menuManager != null) {
                menuManager.openMenu(player, "amis_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "amis_menu");
            }
        });
    }

    public static void startCommandPrompt(final Player player,
                                          final String promptMessage,
                                          final String commandTemplate,
                                          final String reopenMenuId) {
        if (player == null || commandTemplate == null || commandTemplate.isBlank()) {
            if (promptMessage != null && !promptMessage.isBlank()) {
                player.sendMessage(colorize(promptMessage));
            }
            return;
        }
        final ChatInputManager manager = getInstance();
        final MenuManager menuManager = manager.plugin.getMenuManager();
        final String reopenMenu = sanitizeMenuId(reopenMenuId, null);

        player.closeInventory();
        if (promptMessage != null && !promptMessage.isBlank()) {
            player.sendMessage(colorize(promptMessage));
        }
        player.sendMessage("§7Tapez §ccancel §7pour annuler.");

        startInputFlow(player, inputRaw -> {
            final String input = inputRaw == null ? "" : inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cOpération annulée.");
                reopenMenu(menuManager, player, reopenMenu);
                return;
            }
            if (input.isEmpty()) {
                player.sendMessage("§cMerci d'entrer une valeur valide.");
                startCommandPrompt(player, promptMessage, commandTemplate, reopenMenu);
                return;
            }
            final String formatted = commandTemplate.replace("%input%", input).replace("{input}", input);
            final String command = formatted.startsWith("/") ? formatted.substring(1) : formatted;
            if (!command.isBlank()) {
                Bukkit.getScheduler().runTask(manager.plugin, () -> player.performCommand(command));
            }
            reopenMenu(menuManager, player, reopenMenu);
        }, () -> reopenMenu(menuManager, player, reopenMenu));
    }

    public static void startClanCreationFlow(final Player player,
                                             final String promptMessage,
                                             final String reopenMenuId) {
        if (player == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        final ClanManager clanManager = manager.plugin.getClanManager();
        if (clanManager == null) {
            player.sendMessage("§cLa création de clan est indisponible pour le moment.");
            return;
        }
        final MenuManager menuManager = manager.plugin.getMenuManager();
        final String reopenMenu = sanitizeMenuId(reopenMenuId, "clan_menu");

        player.closeInventory();
        promptClanName(manager, player, clanManager, menuManager, reopenMenu, promptMessage);
    }

    public static void startGroupCreateFlow(final Player player) {
        if (player == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        player.closeInventory();
        player.sendMessage("§e§l» Créer un groupe");
        player.sendMessage("§7Tapez le nom de votre groupe:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        final MenuManager menuManager = manager.plugin.getMenuManager();
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cCréation annulée");
                if (menuManager != null) {
                    menuManager.openMenu(player, "groupe_menu");
                }
                return;
            }
            if (input.isEmpty()) {
                player.sendMessage("§cLe nom du groupe ne peut pas être vide");
                if (menuManager != null) {
                    menuManager.openMenu(player, "groupe_menu");
                }
                return;
            }
            final GroupManager groupManager = manager.plugin.getGroupManager();
            if (groupManager.createGroup(player, input, false)) {
                player.sendMessage("§aGroupe '" + input + "' créé!");
            } else {
                player.sendMessage("§cErreur lors de la création");
            }
            if (menuManager != null) {
                menuManager.openMenu(player, "groupe_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "groupe_menu");
            }
        });
    }

    public static void startGroupInviteFlow(final Player player) {
        if (player == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        player.closeInventory();
        player.sendMessage("§e§l» Inviter dans le groupe");
        player.sendMessage("§7Tapez le nom du joueur à inviter:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        final MenuManager menuManager = manager.plugin.getMenuManager();
        final GroupManager groupManager = manager.plugin.getGroupManager();
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cInvitation annulée");
                if (menuManager != null) {
                    menuManager.openMenu(player, "groupe_menu");
                }
                return;
            }
            if (groupManager != null) {
                groupManager.inviteToGroup(player, input);
            }
            if (menuManager != null) {
                menuManager.openMenu(player, "groupe_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "groupe_menu");
            }
        });
    }

    public static void startClanInviteFlow(final Player player) {
        if (player == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        player.closeInventory();
        player.sendMessage("§e§l» Inviter au clan");
        player.sendMessage("§7Tapez le nom du joueur à inviter:");
        player.sendMessage("§7Tapez §ccancel §7pour annuler");

        final MenuManager menuManager = manager.plugin.getMenuManager();
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cInvitation annulée");
                if (menuManager != null) {
                    menuManager.openMenu(player, "clan_menu");
                }
                return;
            }
            final ClanManager clanManager = manager.plugin.getClanManager();
            final boolean success = clanManager.inviteToClan(player, input, "");
            if (!success) {
                player.sendMessage("§cImpossible d'inviter ce joueur");
            }
            if (menuManager != null) {
                menuManager.openMenu(player, "clan_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "clan_menu");
            }
        });
    }

    public static void startInputFlow(final Player player, final Consumer<String> onInput) {
        startInputFlow(player, onInput, () -> {
        });
    }

    public static void startInputFlow(final Player player, final Consumer<String> onInput, final Runnable onTimeout) {
        if (player == null || onInput == null) {
            return;
        }
        final ChatInputManager manager = getInstance();
        manager.startSession(player, onInput, onTimeout != null ? onTimeout : () -> {
        });
    }

    @EventHandler
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final InputSession session = waitingInputs.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        session.cancelTimeout();
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> session.accept(event.getMessage()));
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final InputSession session = waitingInputs.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.cancelTimeout();
        }
    }

    private void startSession(final Player player, final Consumer<String> onInput, final Runnable onTimeout) {
        final UUID uuid = player.getUniqueId();
        final InputSession previous = waitingInputs.remove(uuid);
        if (previous != null) {
            previous.cancelTimeout();
        }
        final InputSession session = new InputSession(onInput);
        waitingInputs.put(uuid, session);
        session.scheduleTimeout(plugin, () -> {
            if (waitingInputs.remove(uuid, session)) {
                Bukkit.getScheduler().runTask(plugin, (Runnable) onTimeout);
            }
        });
    }

    private static void promptClanName(final ChatInputManager manager,
                                       final Player player,
                                       final ClanManager clanManager,
                                       final MenuManager menuManager,
                                       final String reopenMenu,
                                       final String promptMessage) {
        if (promptMessage != null && !promptMessage.isBlank()) {
            player.sendMessage(colorize(promptMessage));
        } else {
            player.sendMessage("§eEntrez le nom de votre clan (3 à 20 caractères).");
        }
        player.sendMessage("§7Tapez §ccancel §7pour annuler.");
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw == null ? "" : inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cCréation de clan annulée.");
                reopenMenu(menuManager, player, reopenMenu);
                return;
            }
            if (input.length() < 3 || input.length() > 20) {
                player.sendMessage("§cLe nom du clan doit faire entre 3 et 20 caractères.");
                promptClanName(manager, player, clanManager, menuManager, reopenMenu,
                        "§eEntrez un nom de clan valide (3 à 20 caractères).");
                return;
            }
            promptClanTag(manager, player, clanManager, menuManager, reopenMenu, input);
        }, () -> reopenMenu(menuManager, player, reopenMenu));
    }

    private static void promptClanTag(final ChatInputManager manager,
                                      final Player player,
                                      final ClanManager clanManager,
                                      final MenuManager menuManager,
                                      final String reopenMenu,
                                      final String clanName) {
        player.sendMessage("§eEntrez le tag de votre clan (2 à 6 caractères).");
        player.sendMessage("§7Tapez §ccancel §7pour annuler.");
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw == null ? "" : inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cCréation de clan annulée.");
                reopenMenu(menuManager, player, reopenMenu);
                return;
            }
            if (input.length() < 2 || input.length() > 6) {
                player.sendMessage("§cLe tag du clan doit faire entre 2 et 6 caractères.");
                promptClanTag(manager, player, clanManager, menuManager, reopenMenu, clanName);
                return;
            }
            promptClanConfirmation(manager, player, clanManager, menuManager, reopenMenu,
                    clanName, input.toUpperCase(Locale.ROOT));
        }, () -> reopenMenu(menuManager, player, reopenMenu));
    }

    private static void promptClanConfirmation(final ChatInputManager manager,
                                               final Player player,
                                               final ClanManager clanManager,
                                               final MenuManager menuManager,
                                               final String reopenMenu,
                                               final String clanName,
                                               final String clanTag) {
        player.sendMessage("§7Nom choisi : §b" + clanName);
        player.sendMessage("§7Tag choisi : §3[" + clanTag + "]");
        player.sendMessage("§eTapez §aconfirmer §epour valider ou §ccancel §epour annuler.");
        startInputFlow(player, inputRaw -> {
            final String input = inputRaw == null ? "" : inputRaw.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cCréation de clan annulée.");
                reopenMenu(menuManager, player, reopenMenu);
                return;
            }
            if (input.equalsIgnoreCase("confirmer") || input.equalsIgnoreCase("confirm")
                    || input.equalsIgnoreCase("oui")) {
                clanManager.createClan(player, clanName, clanTag);
                reopenMenu(menuManager, player, reopenMenu);
                return;
            }
            player.sendMessage("§cRéponse invalide. Tapez 'confirmer' pour valider ou 'cancel' pour annuler.");
            promptClanConfirmation(manager, player, clanManager, menuManager, reopenMenu, clanName, clanTag);
        }, () -> reopenMenu(menuManager, player, reopenMenu));
    }

    private static void reopenMenu(final MenuManager menuManager, final Player player, final String menuId) {
        if (menuManager == null || player == null || menuId == null || menuId.isBlank()) {
            return;
        }
        menuManager.openMenu(player, menuId);
    }

    private static String sanitizeMenuId(final String raw, final String fallback) {
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        return fallback;
    }

    private static String colorize(final String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private static final class InputSession {
        private final Consumer<String> onInput;
        private BukkitTask timeoutTask;

        private InputSession(final Consumer<String> onInput) {
            this.onInput = onInput;
        }

        private void scheduleTimeout(final LobbyPlugin plugin, final Runnable timeoutCallback) {
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, (Runnable) timeoutCallback, 600L);
        }

        private void cancelTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
        }

        private void accept(final String message) {
            onInput.accept(message);
        }
    }
}
