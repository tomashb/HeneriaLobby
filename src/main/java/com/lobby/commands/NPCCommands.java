package com.lobby.commands;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.NPC;
import com.lobby.npcs.NPCManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NPCCommands implements CommandExecutor, TabCompleter {

    private final LobbyPlugin plugin;
    private final NPCManager npcManager;

    public NPCCommands(final LobbyPlugin plugin) {
        this(plugin, plugin != null ? plugin.getNpcManager() : null);
    }

    public NPCCommands(final NPCManager npcManager) {
        this(LobbyPlugin.getInstance(), npcManager);
    }

    private NPCCommands(final LobbyPlugin plugin, final NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (!commandName.equals("lobbyadmin") && !commandName.equals("npc")) {
            return false;
        }

        if (npcManager == null) {
            MessageUtils.sendPrefixedMessage(sender, "&cGestionnaire de PNJ indisponible.");
            return true;
        }

        if (commandName.equals("npc")) {
            return handleNpcCommand(sender, args);
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("npc")) {
            sendUsage(sender);
            return true;
        }
        final String[] npcArgs = Arrays.copyOfRange(args, 1, args.length);
        handleNpcCommand(sender, npcArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (!commandName.equals("lobbyadmin") && !commandName.equals("npc")) {
            return Collections.emptyList();
        }

        if (commandName.equals("npc")) {
            return tabComplete(sender, args);
        }

        if (args.length == 1) {
            return filterSuggestions(List.of("npc"), args[0]);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("npc")) {
            final String[] npcArgs = Arrays.copyOfRange(args, 1, args.length);
            return tabComplete(sender, npcArgs);
        }

        return Collections.emptyList();
    }

    public boolean handle(final CommandSender sender, final String[] args) {
        if (npcManager == null) {
            MessageUtils.sendPrefixedMessage(sender, "&cGestionnaire de PNJ indisponible.");
            return true;
        }
        return handleNpcCommand(sender, args);
    }

    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterSuggestions(List.of("create", "delete", "list", "info", "addaction", "equip", "setcolor"), args[0]);
        }

        if (args.length == 2) {
            final String subCommand = args[0].toLowerCase(Locale.ROOT);
            if (subCommand.equals("delete") || subCommand.equals("info") || subCommand.equals("addaction")
                    || subCommand.equals("equip") || subCommand.equals("setcolor")) {
                return filterSuggestions(new ArrayList<>(npcManager.getNPCNames()), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("addaction")) {
            return filterSuggestions(List.of("[MESSAGE] ", "[SOUND] ", "[COMMAND] ", "[COINS_ADD] ", "[TOKENS_ADD] "), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setcolor")) {
            return filterSuggestions(List.of("#FF0000", "#00FF00", "#0000FF"), args[2]);
        }

        return Collections.emptyList();
    }

    private boolean handleNpcCommand(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        final String[] parameters = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "create" -> handleCreate(sender, parameters);
            case "delete" -> handleDelete(sender, parameters);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, parameters);
            case "addaction" -> handleAddAction(sender, parameters);
            case "equip" -> handleEquip(sender, parameters);
            case "setcolor" -> handleSetColor(sender, parameters);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleCreate(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendPrefixedMessage(sender, "&cSeuls les joueurs peuvent créer des PNJ !");
            return;
        }
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /ladmin npc create <nom> <nom_affiché> [tête]");
            return;
        }

        final String name = args[0];
        final String displayName = args[1];
        final String headTexture = args.length >= 3 ? args[2] : player.getName();

        final List<String> defaultActions = List.of(
                "[SOUND] ENTITY_VILLAGER_YES"
        );

        try {
            npcManager.createNPC(name, displayName, player.getLocation(), headTexture, defaultActions);
            MessageUtils.sendPrefixedMessage(sender, "&aPNJ '&6" + name + "&a' créé avec succès !");
            MessageUtils.sendPrefixedMessage(sender, "&7Position: " + formatLocation(player.getLocation()));
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&c" + exception.getMessage());
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la création du PNJ !");
            if (plugin != null) {
                plugin.getLogger().severe("Error creating NPC: " + exception.getMessage());
            }
        }
    }

    private void handleDelete(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }
        if (args.length == 0) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /ladmin npc delete <nom>");
            return;
        }

        final String name = args[0];

        try {
            npcManager.deleteNPC(name);
            MessageUtils.sendPrefixedMessage(sender, "&aPNJ '&6" + name + "&a' supprimé avec succès !");
        } catch (final IllegalArgumentException ignored) {
            MessageUtils.sendPrefixedMessage(sender, "&cPNJ '&6" + name + "&c' introuvable !");
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de la suppression du PNJ !");
            if (plugin != null) {
                plugin.getLogger().severe("Error deleting NPC: " + exception.getMessage());
            }
        }
    }

    private void handleList(final CommandSender sender) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }

        final List<String> names = new ArrayList<>(npcManager.getNPCNames());
        if (names.isEmpty()) {
            MessageUtils.sendPrefixedMessage(sender, "&cAucun PNJ trouvé.");
            return;
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        MessageUtils.sendPrefixedMessage(sender, "&6&lListe des PNJ (&e" + names.size() + "&6)&7:");
        for (final String name : names) {
            final NPC npc = npcManager.getNPC(name);
            if (npc == null) {
                continue;
            }
            final var data = npc.getData();
            MessageUtils.sendPrefixedMessage(sender, "&e- &a" + name + " &7(" + data.world() + " "
                    + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", data.x(), data.y(), data.z()) + ")");
        }
    }

    private void handleInfo(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }
        if (args.length == 0) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /ladmin npc info <nom>");
            return;
        }

        final NPC npc = npcManager.getNPC(args[0]);
        if (npc == null) {
            MessageUtils.sendPrefixedMessage(sender, "&cPNJ '&6" + args[0] + "&c' introuvable !");
            return;
        }

        final var data = npc.getData();
        MessageUtils.sendPrefixedMessage(sender, "&6&lInformations - " + data.name());
        MessageUtils.sendPrefixedMessage(sender, "&eNom affiché: &f" + (data.displayName() != null ? data.displayName() : "Aucun"));
        MessageUtils.sendPrefixedMessage(sender, "&eMonde: &f" + data.world());
        MessageUtils.sendPrefixedMessage(sender, "&ePosition: &f" + String.format(Locale.ROOT,
                "%.1f, %.1f, %.1f", data.x(), data.y(), data.z()));
        MessageUtils.sendPrefixedMessage(sender, "&eTête: &f" + (data.headTexture() != null ? data.headTexture() : "Par défaut"));
        MessageUtils.sendPrefixedMessage(sender, "&eActions (&6" + data.actions().size() + "&e) :");

        final List<String> actions = data.actions();
        for (int index = 0; index < actions.size(); index++) {
            MessageUtils.sendPrefixedMessage(sender, "&a  " + (index + 1) + ". &f" + actions.get(index));
        }
    }

    private void handleAddAction(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /ladmin npc addaction <nom> <action>");
            MessageUtils.sendPrefixedMessage(sender, "&7Exemples d'actions:");
            MessageUtils.sendPrefixedMessage(sender, "&e  [MESSAGE] &aSalut %player_name% !");
            MessageUtils.sendPrefixedMessage(sender, "&e  [COINS_ADD] 100");
            MessageUtils.sendPrefixedMessage(sender, "&e  [SOUND] ENTITY_VILLAGER_YES");
            return;
        }

        final String name = args[0];
        final NPC npc = npcManager.getNPC(name);
        if (npc == null) {
            MessageUtils.sendPrefixedMessage(sender, "&cPNJ '&6" + name + "&c' introuvable !");
            return;
        }

        final String action = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final List<String> currentActions = new ArrayList<>(npc.getData().actions());
        currentActions.add(action);

        try {
            npcManager.updateNPCActions(name, currentActions);
            MessageUtils.sendPrefixedMessage(sender, "&aAction ajoutée au PNJ '&6" + name + "&a' !");
            MessageUtils.sendPrefixedMessage(sender, "&7Action: &f" + action);
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cErreur lors de l'ajout de l'action !");
            if (plugin != null) {
                plugin.getLogger().severe("Error adding NPC action: " + exception.getMessage());
            }
        }
    }

    private void handleEquip(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendPrefixedMessage(sender, "&cSeuls les joueurs peuvent équiper des PNJ !");
            return;
        }
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }
        if (args.length == 0) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /ladmin npc equip <nom>");
            MessageUtils.sendPrefixedMessage(sender, "&7Tenez l'item à équiper dans votre main principale");
            return;
        }

        final String name = args[0];
        final NPC npc = npcManager.getNPC(name);
        if (npc == null) {
            MessageUtils.sendPrefixedMessage(player, "&cPNJ '&6" + name + "&c' introuvable !");
            return;
        }

        final ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            MessageUtils.sendPrefixedMessage(player, "&cVous devez tenir un item dans votre main !");
            return;
        }

        final ArmorStand armorStand = npc.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            MessageUtils.sendPrefixedMessage(player, "&cLe PNJ n'est pas disponible actuellement.");
            return;
        }

        final var equipment = armorStand.getEquipment();
        if (equipment == null) {
            MessageUtils.sendPrefixedMessage(player, "&cImpossible d'équiper ce PNJ.");
            return;
        }

        equipment.setItemInMainHand(itemInHand.clone());
        MessageUtils.sendPrefixedMessage(player, "&aPNJ '&6" + name + "&a' équipé avec l'item !");
    }

    private void handleSetColor(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.npc")) {
            MessageUtils.sendPrefixedMessage(sender, "&cVous n'avez pas la permission !");
            return;
        }

        if (args.length != 2) {
            MessageUtils.sendPrefixedMessage(sender, "&cUsage: /npc setcolor <nom> <#RRGGBB>");
            return;
        }

        final String name = args[0];
        final String hexColor = args[1];

        if (!npcManager.isValidArmorColor(hexColor)) {
            MessageUtils.sendPrefixedMessage(sender, "&cFormat de couleur invalide (ex: #RRGGBB)");
            return;
        }

        final NPC npc = npcManager.getNPC(name);
        if (npc == null) {
            MessageUtils.sendPrefixedMessage(sender, "&cPNJ introuvable");
            return;
        }

        try {
            npcManager.updateNPCArmorColor(name, hexColor);
            MessageUtils.sendPrefixedMessage(sender, "&aCouleur du PNJ '&6" + name + "&a' mise à jour !");
        } catch (final IllegalArgumentException exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cFormat de couleur invalide (ex: #RRGGBB)");
        } catch (final Exception exception) {
            MessageUtils.sendPrefixedMessage(sender, "&cImpossible de mettre à jour la couleur de ce PNJ.");
            if (plugin != null) {
                plugin.getLogger().severe("Error updating NPC color: " + exception.getMessage());
            }
        }
    }

    private void sendUsage(final CommandSender sender) {
        MessageUtils.sendPrefixedMessage(sender, "&cCommande inconnue ! Utilisez:");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc create <nom> <nom_affiché> [tête]");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc delete <nom>");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc list");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc info <nom>");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc addaction <nom> <action>");
        MessageUtils.sendPrefixedMessage(sender, "&e/ladmin npc equip <nom>");
        MessageUtils.sendPrefixedMessage(sender, "&e/npc setcolor <nom> <#RRGGBB>");
    }

    private List<String> filterSuggestions(final List<String> options, final String prefix) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        final String effectivePrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final List<String> results = new ArrayList<>();
        for (final String option : options) {
            if (option == null) {
                continue;
            }
            if (effectivePrefix.isEmpty() || option.toLowerCase(Locale.ROOT).startsWith(effectivePrefix)) {
                results.add(option);
            }
        }
        return results;
    }

    private String formatLocation(final Location location) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }
}
