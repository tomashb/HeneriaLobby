package com.lobby.menus.confirmation;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, ConfirmationRequest> requests = new ConcurrentHashMap<>();

    public ConfirmationManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openConfirmation(final Player player, final ConfirmationRequest request) {
        if (player == null || request == null) {
            return;
        }
        requests.put(player.getUniqueId(), request);
        if (plugin.getMenuManager() == null) {
            LogUtils.warning(plugin, "Attempted to open confirmation menu without MenuManager available.");
            requests.remove(player.getUniqueId());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMenuManager().openMenu(player, request.templateId()));
    }

    public void executeConfirmation(final Player player, final boolean confirmed) {
        if (player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        final ConfirmationRequest request = requests.remove(uuid);
        if (request == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
        final ActionProcessor processor = resolveActionProcessor();
        if (processor == null) {
            return;
        }
        final List<String> actions = confirmed ? request.confirmActions() : request.cancelActions();
        if (actions.isEmpty()) {
            return;
        }
        processor.processActions(actions, player, null);
    }

    public ConfirmationRequest getRequest(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return requests.get(uuid);
    }

    public void clearRequest(final UUID uuid) {
        if (uuid == null) {
            return;
        }
        requests.remove(uuid);
    }

    public void clearAll() {
        requests.clear();
    }

    public String applyPlaceholders(final Player player, final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final ConfirmationRequest request = player == null ? null : requests.get(player.getUniqueId());
        if (request == null) {
            return text;
        }
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%action_description%", defaultString(request.actionDescription(), ""));
        replacements.put("%action_title%", defaultString(request.actionTitle(), "&cAction"));
        replacements.put("%action_icon%", defaultString(request.actionIcon(), "hdb:5390"));
        replacements.put("%action_details%", defaultString(request.joinedDetails(), ""));
        replacements.put("%previous_menu%", defaultString(request.previousMenuId(), ""));
        replacements.put("%confirmation_action%", "[CONFIRM_EXECUTE]");
        String replaced = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    public List<String> applyPlaceholders(final Player player, final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(line -> applyPlaceholders(player, line))
                .toList();
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (!requests.containsKey(uuid)) {
            return;
        }
        requests.remove(uuid);
    }

    private ActionProcessor resolveActionProcessor() {
        return plugin.getNpcManager() != null ? plugin.getNpcManager().getActionProcessor() : null;
    }

    private String defaultString(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
