package com.lobby.holograms;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PlaceholderProcessor {

    private final LobbyPlugin plugin;
    private final Map<String, Function<Player, String>> handlers = new HashMap<>();

    public PlaceholderProcessor(final LobbyPlugin plugin) {
        this.plugin = plugin;
        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put("player_name", player -> player != null ? player.getName() : "N/A");
        handlers.put("player_coins", player -> String.valueOf(resolveEconomyManager()
                .map(manager -> player == null ? 0L : manager.getCoins(player.getUniqueId()))
                .orElse(0L)));
        handlers.put("player_tokens", player -> String.valueOf(resolveEconomyManager()
                .map(manager -> player == null ? 0L : manager.getTokens(player.getUniqueId()))
                .orElse(0L)));

        handlers.put("server_online", player -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        handlers.put("server_max", player -> String.valueOf(Bukkit.getMaxPlayers()));

        handlers.put("time", player -> {
            final LocalTime now = LocalTime.now();
            return String.format("%02d:%02d", now.getHour(), now.getMinute());
        });
        handlers.put("date", player -> {
            final LocalDate now = LocalDate.now();
            return String.format("%02d/%02d/%d", now.getDayOfMonth(), now.getMonthValue(), now.getYear());
        });

        for (int position = 1; position <= 10; position++) {
            final int index = position;
            handlers.put("top_coins_" + position, player -> getTopPlayer("coins", index));
            handlers.put("top_tokens_" + position, player -> getTopPlayer("tokens", index));
        }
    }

    public String process(final String text, final Player player) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String processed = text;
        for (Map.Entry<String, Function<Player, String>> entry : handlers.entrySet()) {
            final String placeholder = "%" + entry.getKey() + "%";
            if (!processed.contains(placeholder)) {
                continue;
            }
            try {
                final String value = entry.getValue().apply(player);
                processed = processed.replace(placeholder, value);
            } catch (final Exception exception) {
                processed = processed.replace(placeholder, "ERROR");
            }
        }
        return processed;
    }

    private String getTopPlayer(final String type, final int position) {
        try {
            final Optional<EconomyManager> manager = resolveEconomyManager();
            if (manager.isEmpty()) {
                return "Aucun";
            }
            final List<String> top = "coins".equalsIgnoreCase(type)
                    ? manager.get().getTopCoins(position)
                    : manager.get().getTopTokens(position);
            if (position < 1 || position > top.size()) {
                return "Aucun";
            }
            final String entry = top.get(position - 1);
            final int separatorIndex = entry.indexOf(':');
            if (separatorIndex <= 0) {
                return entry;
            }
            final String name = entry.substring(0, separatorIndex);
            final String amount = entry.substring(separatorIndex + 1);
            return name + " - " + amount;
        } catch (final Exception exception) {
            return "Error";
        }
    }

    private Optional<EconomyManager> resolveEconomyManager() {
        return Optional.ofNullable(plugin.getEconomyManager());
    }
}
