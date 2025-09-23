package com.lobby.menus.confirmation;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationManager {

    private final LobbyPlugin plugin;
    private final Map<UUID, ConfirmationRequest> requests = new ConcurrentHashMap<>();

    public ConfirmationManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openConfirmation(final Player player, final ConfirmationRequest request) {
        if (player == null || request == null) {
            return;
        }
        requests.put(player.getUniqueId(), request);
        player.sendMessage("§c§lConfirmation requise");
        if (request.actionTitle() != null && !request.actionTitle().isBlank()) {
            player.sendMessage("§7Action: §f" + request.actionTitle());
        }
        if (request.actionDescription() != null && !request.actionDescription().isBlank()) {
            player.sendMessage("§7" + request.actionDescription());
        }
        if (!request.actionDetails().isEmpty()) {
            request.actionDetails().forEach(line -> player.sendMessage("§8» §7" + line));
        }
        player.sendMessage("§7Tapez §aconfirm §7ou §ccancel §7dans le chat.");
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
        final ActionProcessor processor = resolveActionProcessor();
        if (processor == null) {
            return;
        }
        final List<String> actions = confirmed ? request.confirmActions() : request.cancelActions();
        if (actions.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> processor.processActions(actions, player, null));
    }

    public ConfirmationRequest getRequest(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return requests.get(uuid);
    }

    public void clearRequest(final UUID uuid) {
        if (uuid != null) {
            requests.remove(uuid);
        }
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
        String processed = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            processed = processed.replace(entry.getKey(), entry.getValue());
        }
        return processed;
    }

    public List<String> applyPlaceholders(final Player player, final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream().map(line -> applyPlaceholders(player, line)).toList();
    }

    private ActionProcessor resolveActionProcessor() {
        if (plugin.getNpcManager() == null) {
            LogUtils.warning(plugin, "NPC manager unavailable for confirmation execution.");
            return null;
        }
        return plugin.getNpcManager().getActionProcessor();
    }

    private String defaultString(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
