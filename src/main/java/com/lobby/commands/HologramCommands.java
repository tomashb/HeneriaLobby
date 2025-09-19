package com.lobby.commands;

import com.lobby.data.HologramData;
import com.lobby.holograms.Hologram;
import com.lobby.holograms.HologramManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HologramCommands {

    private final HologramManager hologramManager;

    public HologramCommands(final HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public boolean handle(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "create" -> handleCreate(sender, Arrays.copyOfRange(args, 1, args.length));
            case "delete" -> handleDelete(sender, Arrays.copyOfRange(args, 1, args.length));
            case "edit", "setlines", "lines" -> handleEdit(sender, Arrays.copyOfRange(args, 1, args.length));
            case "list" -> handleList(sender);
            case "tp", "teleport" -> handleTeleport(sender, Arrays.copyOfRange(args, 1, args.length));
            case "move" -> handleMove(sender, Arrays.copyOfRange(args, 1, args.length));
            case "info" -> handleInfo(sender, Arrays.copyOfRange(args, 1, args.length));
            case "setline" -> handleSetLine(sender, Arrays.copyOfRange(args, 1, args.length));
            case "addline" -> handleAddLine(sender, Arrays.copyOfRange(args, 1, args.length));
            case "insertline" -> handleInsertLine(sender, Arrays.copyOfRange(args, 1, args.length));
            case "removeline" -> handleRemoveLine(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clearlines" -> handleClearLines(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "delete", "edit", "list", "teleport", "tp", "move", "info",
                    "setline", "addline", "insertline", "removeline", "clearlines"), args[0]);
        }
        if (args.length == 2) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("delete") || sub.equals("edit") || sub.equals("teleport") || sub.equals("tp") || sub.equals("move") || sub.equals("info") ||
                    sub.equals("setline") || sub.equals("addline") || sub.equals("insertline") || sub.equals("removeline") ||
                    sub.equals("clearlines")) {
                return filter(new ArrayList<>(hologramManager.getHologramNames()), args[1]);
            }
        }
        return List.of();
    }

    private boolean handleMove(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "economy.player_only");
            return true;
        }
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_move");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        try {
            hologramManager.moveHologram(name, player.getLocation());
            MessageUtils.sendConfigMessage(sender, "hologram.moved", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            final String message = exception.getMessage();
            if (message != null && message.contains("not found")) {
                MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            } else if (message != null && !message.isBlank()) {
                MessageUtils.sendPrefixedMessage(sender, "&c" + message);
            } else {
                MessageUtils.sendPrefixedMessage(sender, "&cImpossible de déplacer cet hologramme.");
            }
        } catch (final IllegalStateException exception) {
            final String message = exception.getMessage() != null ? exception.getMessage() : "Erreur";
            MessageUtils.sendPrefixedMessage(sender, "&c" + message);
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors du déplacement de l'hologramme !");
            hologramManager.getPlugin().getLogger().warning("Failed to move hologram: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleCreate(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "economy.player_only");
            return true;
        }
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_create");
            return true;
        }
        final String name = args[0];
        final List<String> lines = new ArrayList<>();
        lines.addAll(Arrays.asList(args).subList(1, args.length));
        try {
            hologramManager.createHologram(name, player.getLocation(), lines);
            MessageUtils.sendConfigMessage(sender, "hologram.created", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            final String message = exception.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("already exists")) {
                MessageUtils.sendConfigMessage(sender, "hologram.already_exists", Map.of("name", name));
            } else {
                final String text = message != null ? message : "Erreur";
                MessageUtils.sendPrefixedMessage(sender, "&c" + text);
            }
        } catch (final IllegalStateException exception) {
            final String text = exception.getMessage() != null ? exception.getMessage() : "Erreur";
            MessageUtils.sendPrefixedMessage(sender, "&c" + text);
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la création de l'hologramme !");
            hologramManager.getPlugin().getLogger().warning("Failed to create hologram: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleDelete(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_delete");
            return true;
        }
        final String name = args[0];
        try {
            hologramManager.deleteHologram(name);
            MessageUtils.sendConfigMessage(sender, "hologram.deleted", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
        }
        return true;
    }

    private boolean handleEdit(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_edit");
            return true;
        }
        final String name = args[0];
        final List<String> lines = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        try {
            hologramManager.updateHologramLines(name, lines);
            MessageUtils.sendConfigMessage(sender, "hologram.updated", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
        }
        return true;
    }

    private boolean handleList(final CommandSender sender) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        final Set<String> names = hologramManager.getHologramNames();
        if (names.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "hologram.no_holograms");
            return true;
        }
        MessageUtils.sendConfigMessage(sender, "hologram.list_header", Map.of("count", String.valueOf(names.size())));
        for (String name : names) {
            final Hologram hologram = hologramManager.getHologram(name);
            if (hologram == null) {
                continue;
            }
            final HologramData data = hologram.getData();
            MessageUtils.sendConfigMessage(sender, "hologram.list_item", Map.of(
                    "name", name,
                    "world", data.world(),
                    "x", String.format("%.1f", data.x()),
                    "y", String.format("%.1f", data.y()),
                    "z", String.format("%.1f", data.z())
            ));
        }
        return true;
    }

    private boolean handleTeleport(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "economy.player_only");
            return true;
        }
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_teleport");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final HologramData data = hologram.getData();
        final Location location = new Location(Bukkit.getWorld(data.world()), data.x(), data.y(), data.z());
        if (location.getWorld() == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        player.teleport(location);
        MessageUtils.sendConfigMessage(sender, "hologram.teleported", Map.of("name", name));
        return true;
    }

    private boolean handleInfo(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_info");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final HologramData data = hologram.getData();
        MessageUtils.sendConfigMessage(sender, "hologram.info_header", Map.of("name", name));
        MessageUtils.sendConfigMessage(sender, "hologram.info_world", Map.of("world", data.world()));
        MessageUtils.sendConfigMessage(sender, "hologram.info_position", Map.of(
                "x", String.format("%.1f", data.x()),
                "y", String.format("%.1f", data.y()),
                "z", String.format("%.1f", data.z())
        ));
        MessageUtils.sendConfigMessage(sender, "hologram.info_animation", Map.of("animation", data.animation().name()));
        MessageUtils.sendConfigMessage(sender, "hologram.info_lines_header", Map.of("count", String.valueOf(data.lines().size())));
        for (int index = 0; index < data.lines().size(); index++) {
            final String rawLine = data.lines().get(index);
            final String displayLine = rawLine == null || rawLine.trim().isEmpty()
                    ? Optional.ofNullable(MessageUtils.getConfigMessage("hologram.empty_line_note")).orElse("&7(ligne vide)")
                    : rawLine;
            MessageUtils.sendConfigMessage(sender, "hologram.info_line_item", Map.of(
                    "index", String.valueOf(index + 1),
                    "line", displayLine
            ));
        }
        MessageUtils.sendConfigMessage(sender, "hologram.info_help", Map.of("name", name));
        return true;
    }

    private boolean handleSetLine(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_setline");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final int lineNumber;
        try {
            lineNumber = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            final int maxLine = hologram.getData().lines().size() + 1;
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            return true;
        }
        if (lineNumber <= 0) {
            final int maxLine = hologram.getData().lines().size() + 1;
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            return true;
        }
        final String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        try {
            hologramManager.setHologramLine(name, lineNumber - 1, text);
            MessageUtils.sendConfigMessage(sender, "hologram.line_set", Map.of(
                    "name", name,
                    "line_number", String.valueOf(lineNumber)
            ));
        } catch (final IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            } else {
                final int maxLine = hologram.getData().lines().size() + 1;
                MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            }
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la modification de la ligne !");
            hologramManager.getPlugin().getLogger().warning("Failed to set hologram line: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleAddLine(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_addline");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        try {
            hologramManager.addHologramLine(name, text);
            MessageUtils.sendConfigMessage(sender, "hologram.line_added", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de l'ajout de la ligne !");
            hologramManager.getPlugin().getLogger().warning("Failed to add hologram line: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleInsertLine(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_insertline");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final int lineNumber;
        try {
            lineNumber = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            final int maxLine = hologram.getData().lines().size() + 1;
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            return true;
        }
        if (lineNumber <= 0) {
            final int maxLine = hologram.getData().lines().size() + 1;
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            return true;
        }
        final String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        try {
            hologramManager.insertHologramLine(name, lineNumber - 1, text);
            MessageUtils.sendConfigMessage(sender, "hologram.line_inserted", Map.of(
                    "name", name,
                    "line_number", String.valueOf(lineNumber)
            ));
        } catch (final IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            } else {
                final int maxLine = hologram.getData().lines().size() + 1;
                MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(maxLine)));
            }
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de l'insertion de la ligne !");
            hologramManager.getPlugin().getLogger().warning("Failed to insert hologram line: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleRemoveLine(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_removeline");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        final int lineNumber;
        try {
            lineNumber = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            final int maxLine = hologram.getData().lines().size();
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(Math.max(maxLine, 1))));
            return true;
        }
        final int maxLine = hologram.getData().lines().size();
        if (lineNumber <= 0 || lineNumber > maxLine) {
            MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(Math.max(maxLine, 1))));
            return true;
        }
        try {
            hologramManager.removeHologramLine(name, lineNumber - 1);
            MessageUtils.sendConfigMessage(sender, "hologram.line_removed", Map.of(
                    "name", name,
                    "line_number", String.valueOf(lineNumber)
            ));
        } catch (final IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            } else {
                MessageUtils.sendConfigMessage(sender, "hologram.line_out_of_range", Map.of("max_line", String.valueOf(Math.max(maxLine, 1))));
            }
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la suppression de la ligne !");
            hologramManager.getPlugin().getLogger().warning("Failed to remove hologram line: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleClearLines(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.hologram")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "hologram.usage_clearlines");
            return true;
        }
        final String name = args[0];
        final Hologram hologram = hologramManager.getHologram(name);
        if (hologram == null) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
            return true;
        }
        try {
            hologramManager.updateHologramLines(name, List.of("&7Hologramme vide"));
            MessageUtils.sendConfigMessage(sender, "hologram.lines_cleared", Map.of("name", name));
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendConfigMessage(sender, "hologram.not_found", Map.of("name", name));
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la suppression des lignes !");
            hologramManager.getPlugin().getLogger().warning("Failed to clear hologram lines: " + exception.getMessage());
        }
        return true;
    }

    private void sendUsage(final CommandSender sender) {
        MessageUtils.sendConfigMessage(sender, "hologram.usage");
    }

    private List<String> filter(final List<String> options, final String token) {
        if (token == null || token.isEmpty()) {
            return options;
        }
        final String lower = token.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
