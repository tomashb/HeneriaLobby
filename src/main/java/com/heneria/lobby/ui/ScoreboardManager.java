package com.heneria.lobby.ui;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.player.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a configurable scoreboard for players.
 */
public class ScoreboardManager {

    private final HeneriaLobbyPlugin plugin;
    private String title;
    private List<String> lines;
    private final Map<Player, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(HeneriaLobbyPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    private void loadConfig() {
        plugin.saveResource("scoreboard.yml", false);
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&d&lHENERIA"));
        this.lines = config.getStringList("lines");
    }

    private String applyPlaceholders(Player player, String line) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long coins = data != null ? data.getCoins() : 0L;
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        String prefix = "";
        LuckPerms lp = plugin.getLuckPerms();
        if (lp != null) {
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String metaPrefix = user.getCachedData().getMetaData().getPrefix();
                if (metaPrefix != null) {
                    prefix = metaPrefix;
                }
            }
        }

        String result = line
                .replace("%player_coins%", String.valueOf(coins))
                .replace("%luckperms_prefix%", prefix)
                .replace("%server_online%", String.valueOf(online))
                .replace("%server_max_players%", String.valueOf(maxPlayers));
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public void show(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("heneria", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        for (String line : lines) {
            String text = applyPlaceholders(player, line);
            obj.getScore(text).setScore(score--);
        }
        player.setScoreboard(board);
        boards.put(player, board);
    }

    public void update(Player player) {
        if (!boards.containsKey(player)) {
            show(player);
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("heneria", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        for (String line : lines) {
            String text = applyPlaceholders(player, line);
            obj.getScore(text).setScore(score--);
        }
        player.setScoreboard(board);
        boards.put(player, board);
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }
}
