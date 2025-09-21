package com.lobby.social;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.groups.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
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
                    menuManager.openMenu(player, "friends_menu");
                }
                return;
            }
            if (manager.plugin.getFriendManager().sendFriendRequest(player, input)) {
                player.sendMessage("§aDemande envoyée à §6" + input + "§a !");
            } else {
                player.sendMessage("§cImpossible d'envoyer la demande");
            }
            if (menuManager != null) {
                menuManager.openMenu(player, "friends_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "friends_menu");
            }
        });
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
                    menuManager.openMenu(player, "groups_menu");
                }
                return;
            }
            if (input.isEmpty()) {
                player.sendMessage("§cLe nom du groupe ne peut pas être vide");
                if (menuManager != null) {
                    menuManager.openMenu(player, "groups_menu");
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
                menuManager.openMenu(player, "groups_menu");
            }
        }, () -> {
            if (menuManager != null) {
                menuManager.openMenu(player, "groups_menu");
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
        Bukkit.getScheduler().runTask(plugin, () -> session.accept(event.getMessage()));
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
                Bukkit.getScheduler().runTask(plugin, onTimeout);
            }
        });
    }

    private static final class InputSession {
        private final Consumer<String> onInput;
        private BukkitTask timeoutTask;

        private InputSession(final Consumer<String> onInput) {
            this.onInput = onInput;
        }

        private void scheduleTimeout(final LobbyPlugin plugin, final Runnable timeoutCallback) {
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, timeoutCallback, 600L);
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
