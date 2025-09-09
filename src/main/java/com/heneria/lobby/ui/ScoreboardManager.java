package com.heneria.lobby.ui;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.player.PlayerData;
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
        this.title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&5Heneria"));
        this.lines = config.getStringList("lines");
    }

    private String applyPlaceholders(Player player, String line) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long coins = data != null ? data.getCoins() : 0L;
        String result = line
                .replace("%player_coins%", String.valueOf(coins))
                .replace("%lobby_players%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%total_players%", String.valueOf(Bukkit.getOnlinePlayers().size()));
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
