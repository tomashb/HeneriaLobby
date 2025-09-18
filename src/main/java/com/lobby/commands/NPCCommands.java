package com.lobby.commands;

import com.lobby.data.NPCData;
import com.lobby.npcs.NPC;
import com.lobby.npcs.NPCManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NPCCommands {

    private final NPCManager npcManager;

    public NPCCommands(final NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    public boolean handle(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        final String[] remaining = Arrays.copyOfRange(args, 1, args.length);
        return switch (subCommand) {
            case "create" -> handleCreate(sender, remaining);
            case "delete" -> handleDelete(sender, remaining);
            case "list" -> handleList(sender);
            case "addaction" -> handleAddAction(sender, remaining);
            case "info" -> handleInfo(sender, remaining);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "delete", "list", "addaction", "info"), args[0]);
        }
        if (args.length == 2) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("delete") || sub.equals("addaction") || sub.equals("info")) {
                return filter(new ArrayList<>(npcManager.getNPCNames()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addaction")) {
            return filter(List.of("[MESSAGE] ", "[SOUND] ", "[COMMAND] ", "[COINS_ADD] ", "[TOKENS_ADD] "), args[2]);
        }
        return List.of();
    }

    private boolean handleCreate(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "economy.player_only");
            return true;
        }
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "npc.usage_create");
            return true;
        }
        final String name = args[0];
        final String displayName = args[1];
        final String headTexture = args.length >= 3 ? args[2] : player.getName();
        final List<String> defaultActions = List.of(
                "[MESSAGE] &eSalut %player_name% !",
                "[SOUND] ENTITY_VILLAGER_YES"
        );
        try {
            npcManager.createNPC(name, displayName, player.getLocation(), headTexture, defaultActions);
            MessageUtils.sendConfigMessage(sender, "npc.created", Map.of("name", name));
        } catch (final IllegalStateException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&c" + exception.getMessage());
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&c" + exception.getMessage());
        } catch (final RuntimeException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cImpossible de créer le PNJ : " + exception.getMessage());
        }
        return true;
    }

    private boolean handleDelete(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "npc.usage_delete");
            return true;
        }
        final String name = args[0];
        if (npcManager.deleteNPC(name)) {
            MessageUtils.sendConfigMessage(sender, "npc.deleted", Map.of("name", name));
        } else {
            MessageUtils.sendConfigMessage(sender, "npc.not_found", Map.of("name", name));
        }
        return true;
    }

    private boolean handleList(final CommandSender sender) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        final Set<String> names = npcManager.getNPCNames();
        if (names.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "npc.none");
            return true;
        }
        MessageUtils.sendConfigMessage(sender, "npc.list_header", Map.of("count", String.valueOf(names.size())));
        for (final String name : names) {
            final NPC npc = npcManager.getNPC(name);
            if (npc == null) {
                continue;
            }
            final NPCData data = npc.getData();
            MessageUtils.sendConfigMessage(sender, "npc.list_entry", Map.of(
                    "name", name,
                    "world", data.world(),
                    "x", format(data.x()),
                    "y", format(data.y()),
                    "z", format(data.z())
            ));
        }
        return true;
    }

    private boolean handleAddAction(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "npc.usage_addaction");
            return true;
        }
        final String name = args[0];
        final NPC npc = npcManager.getNPC(name);
        if (npc == null) {
            MessageUtils.sendConfigMessage(sender, "npc.not_found", Map.of("name", name));
            return true;
        }
        final String action = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final List<String> actions = new ArrayList<>(npc.getData().actions());
        actions.add(action);
        try {
            npcManager.updateNPCActions(name, actions);
            MessageUtils.sendConfigMessage(sender, "npc.action_added", Map.of("name", name));
        } catch (final RuntimeException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&c" + exception.getMessage());
        }
        return true;
    }

    private boolean handleInfo(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "npc.usage_info");
            return true;
        }
        final String name = args[0];
        final NPC npc = npcManager.getNPC(name);
        if (npc == null) {
            MessageUtils.sendConfigMessage(sender, "npc.not_found", Map.of("name", name));
            return true;
        }
        final NPCData data = npc.getData();
        MessageUtils.sendConfigMessage(sender, "npc.info_header", Map.of("name", name));
        MessageUtils.sendConfigMessage(sender, "npc.info_world", Map.of("world", data.world()));
        MessageUtils.sendConfigMessage(sender, "npc.info_position", Map.of(
                "x", format(data.x()),
                "y", format(data.y()),
                "z", format(data.z())
        ));
        final List<String> actions = data.actions();
        MessageUtils.sendConfigMessage(sender, "npc.info_actions_header", Map.of("count", String.valueOf(actions.size())));
        for (int index = 0; index < actions.size(); index++) {
            final String action = actions.get(index);
            MessageUtils.sendConfigMessage(sender, "npc.info_action_entry", Map.of(
                    "index", String.valueOf(index + 1),
                    "action", action
            ));
        }
        return true;
    }

    private void sendUsage(final CommandSender sender) {
        MessageUtils.sendConfigMessage(sender, "npc.usage");
    }

    private List<String> filter(final List<String> options, final String prefix) {
        final String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private String format(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
