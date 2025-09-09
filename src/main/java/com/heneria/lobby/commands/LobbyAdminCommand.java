package com.heneria.lobby.commands;

import com.heneria.lobby.config.ConfigManager;
import com.heneria.lobby.database.DatabaseManager;
import com.heneria.lobby.util.AdminMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative command for configuring lobby activities.
 */
public class LobbyAdminCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Map<UUID, Location> goalASelections = new HashMap<>();
    private final Map<UUID, Location> goalBSelections = new HashMap<>();

    public LobbyAdminCommand(DatabaseManager databaseManager, ConfigManager configManager) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            AdminMessage.info(sender, "Modules disponibles : parkour, minifoot");
            return true;
        }

        // legacy testdb command
        if (args.length == 1 && args[0].equalsIgnoreCase("testdb")) {
            AdminMessage.info(sender, "Vérification de la base de données...");
            try (Connection ignored = databaseManager.getConnection()) {
                AdminMessage.success(sender, "Connexion à la base de données réussie.");
            } catch (Exception e) {
                AdminMessage.error(sender, "Échec de la connexion : " + e.getMessage());
            }
            return true;
        }

        String module = args[0].toLowerCase();
        if (module.equals("parkour")) {
            if (!(sender instanceof Player player)) {
                AdminMessage.error(sender, "Cette commande doit être exécutée en jeu.");
                return true;
            }
            if (args.length < 2) {
                AdminMessage.info(sender, "Sous-commandes : setspawn, setend, addcheckpoint, removecheckpoint <#>, listcheckpoints");
                return true;
            }
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "setspawn" -> {
                    configManager.setParkourSpawn(player.getLocation());
                    AdminMessage.success(sender, "Le point de spawn du parkour a été défini à votre position.");
                }
                case "setend" -> {
                    configManager.setParkourEnd(player.getLocation());
                    AdminMessage.success(sender, "La plaque d'arrivée du parkour a été définie à votre position.");
                }
                case "addcheckpoint" -> {
                    int index = configManager.addCheckpoint(player.getLocation());
                    AdminMessage.success(sender, "Checkpoint #" + index + " ajouté à votre position.");
                }
                case "removecheckpoint" -> {
                    if (args.length < 3) {
                        AdminMessage.error(sender, "Usage: /" + label + " parkour removecheckpoint <numéro>");
                        return true;
                    }
                    try {
                        int id = Integer.parseInt(args[2]);
                        if (configManager.removeCheckpoint(id)) {
                            AdminMessage.success(sender, "Le checkpoint #" + id + " a été supprimé.");
                        } else {
                            AdminMessage.error(sender, "Le checkpoint #" + id + " n'existe pas.");
                        }
                    } catch (NumberFormatException e) {
                        AdminMessage.error(sender, "Numéro de checkpoint invalide.");
                    }
                }
                case "listcheckpoints" -> {
                    List<String> cps = configManager.getCheckpoints();
                    if (cps.isEmpty()) {
                        AdminMessage.info(sender, "Aucun checkpoint défini.");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < cps.size(); i++) {
                            String[] p = cps.get(i).split(",");
                            sb.append("[#").append(i + 1).append(": ")
                                    .append(p[1]).append(", ")
                                    .append(p[2]).append(", ")
                                    .append(p[3]).append("]");
                            if (i + 1 < cps.size()) sb.append(", ");
                        }
                        AdminMessage.info(sender, "Liste des checkpoints : " + sb);
                    }
                }
                default -> AdminMessage.error(sender, "Sous-commande inconnue pour le parkour.");
            }
            return true;
        }

        if (module.equals("minifoot")) {
            if (!(sender instanceof Player player)) {
                AdminMessage.error(sender, "Cette commande doit être exécutée en jeu.");
                return true;
            }
            if (args.length < 2) {
                AdminMessage.info(sender, "Sous-commandes : setspawn, setgoal1, setgoal2");
                return true;
            }
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "setspawn" -> {
                    configManager.setMiniFootSpawn(player.getLocation());
                    AdminMessage.success(sender, "Le spawn du ballon a été défini à votre position.");
                }
                case "setgoal1" -> handleGoalSelection(player, goalASelections, "a");
                case "setgoal2" -> handleGoalSelection(player, goalBSelections, "b");
                default -> AdminMessage.error(sender, "Sous-commande inconnue pour le mini-foot.");
            }
            return true;
        }

        AdminMessage.error(sender, "Module inconnu : " + args[0]);
        return true;
    }

    private void handleGoalSelection(Player player, Map<UUID, Location> map, String which) {
        UUID id = player.getUniqueId();
        if (!map.containsKey(id)) {
            map.put(id, player.getLocation());
            AdminMessage.info(player, "Premier point défini, exécutez à nouveau la commande à l'autre coin.");
        } else {
            Location first = map.remove(id);
            configManager.setMiniFootGoal(which, first, player.getLocation());
            AdminMessage.success(player, "La cage de but " + (which.equals("a") ? "1" : "2") + " a été définie.");
        }
    }
}

