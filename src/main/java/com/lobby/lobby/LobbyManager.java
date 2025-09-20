package com.lobby.lobby;

import com.lobby.LobbyPlugin;
import com.lobby.lobby.items.LobbyItemManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyManager {

    private final LobbyPlugin plugin;
    private final Set<UUID> bypassPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final LobbyItemManager itemManager;
    private Location lobbySpawn;

    public LobbyManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        itemManager = new LobbyItemManager(plugin);
        loadSpawn();
    }

    public void reload() {
        loadSpawn();
        applyWorldSettings();
        itemManager.reload();
    }

    public void loadSpawn() {
        final FileConfiguration config = plugin.getConfig();
        final String worldName = config.getString("lobby.spawn.world");
        if (worldName == null || worldName.isEmpty()) {
            lobbySpawn = null;
            return;
        }

        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby spawn world '" + worldName + "' introuvable.");
            lobbySpawn = null;
            return;
        }

        final double x = config.getDouble("lobby.spawn.x");
        final double y = config.getDouble("lobby.spawn.y");
        final double z = config.getDouble("lobby.spawn.z");
        final float yaw = (float) config.getDouble("lobby.spawn.yaw");
        final float pitch = (float) config.getDouble("lobby.spawn.pitch");
        lobbySpawn = new Location(world, x, y, z, yaw, pitch);
    }

    public void saveSpawn(final Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        lobbySpawn = location.clone();
        final FileConfiguration config = plugin.getConfig();
        config.set("lobby.spawn.world", location.getWorld().getName());
        config.set("lobby.spawn.x", location.getX());
        config.set("lobby.spawn.y", location.getY());
        config.set("lobby.spawn.z", location.getZ());
        config.set("lobby.spawn.yaw", location.getYaw());
        config.set("lobby.spawn.pitch", location.getPitch());
        plugin.saveConfig();
        applyWorldSettings();
    }

    public Location getLobbySpawn() {
        if (lobbySpawn != null && lobbySpawn.getWorld() != null) {
            return lobbySpawn.clone();
        }
        final World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return defaultWorld != null ? defaultWorld.getSpawnLocation() : null;
    }

    public boolean hasLobbySpawn() {
        return lobbySpawn != null && lobbySpawn.getWorld() != null;
    }

    public boolean isBypassing(final Player player) {
        return player != null && bypassPlayers.contains(player.getUniqueId());
    }

    public boolean toggleBypass(final Player player) {
        if (player == null) {
            return false;
        }
        final UUID uniqueId = player.getUniqueId();
        if (bypassPlayers.contains(uniqueId)) {
            bypassPlayers.remove(uniqueId);
            preparePlayer(player, PreparationCause.COMMAND);
            return false;
        }
        bypassPlayers.add(uniqueId);
        itemManager.removeProtection(uniqueId);
        itemManager.removeLobbyItems(player.getInventory());
        player.updateInventory();
        return true;
    }

    public void removeBypass(final UUID uniqueId) {
        if (uniqueId != null) {
            bypassPlayers.remove(uniqueId);
            itemManager.removeProtection(uniqueId);
        }
    }

    public void applyWorldSettings() {
        final World world = getLobbyWorld();
        if (world == null) {
            return;
        }
        final FileConfiguration config = plugin.getConfig();
        final ConfigurationSection worldSection = config.getConfigurationSection("lobby.world");
        if (worldSection != null) {
            if (worldSection.getBoolean("freeze_time", true)) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(worldSection.getLong("time", 6000L));
            } else {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            }

            if (worldSection.getBoolean("freeze_weather", true)) {
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
                world.setThunderDuration(0);
            } else {
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            }

            final ConfigurationSection gamerules = worldSection.getConfigurationSection("gamerules");
            if (gamerules != null) {
                if (gamerules.contains("do_mob_spawning")) {
                    world.setGameRule(GameRule.DO_MOB_SPAWNING, gamerules.getBoolean("do_mob_spawning"));
                }
                if (gamerules.contains("do_fire_tick")) {
                    world.setGameRule(GameRule.DO_FIRE_TICK, gamerules.getBoolean("do_fire_tick"));
                }
                if (gamerules.contains("keep_inventory")) {
                    world.setGameRule(GameRule.KEEP_INVENTORY, gamerules.getBoolean("keep_inventory"));
                }
                if (gamerules.contains("show_death_messages")) {
                    world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, gamerules.getBoolean("show_death_messages"));
                }
                if (gamerules.contains("random_tick_speed")) {
                    world.setGameRule(GameRule.RANDOM_TICK_SPEED, gamerules.getInt("random_tick_speed"));
                }
            }
        }
    }

    public double getVoidTeleportY() {
        return plugin.getConfig().getDouble("lobby.protection.void_teleport_y", 0.0D);
    }

    public void preparePlayer(final Player player) {
        preparePlayer(player, PreparationCause.JOIN);
    }

    public void preparePlayer(final Player player, final PreparationCause cause) {
        if (player == null) {
            return;
        }
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        itemManager.apply(player, cause);
    }

    public boolean teleportToLobby(final Player player) {
        final Location spawn = getLobbySpawn();
        if (player == null || spawn == null) {
            return false;
        }
        final boolean result = player.teleport(spawn);
        if (result) {
            player.setFallDistance(0.0F);
            player.setFireTicks(0);
        }
        return result;
    }

    public boolean isLobbyWorld(final World world) {
        final World spawnWorld = hasLobbySpawn() ? Objects.requireNonNull(lobbySpawn).getWorld() : getLobbyWorld();
        return spawnWorld != null && spawnWorld.equals(world);
    }

    public World getLobbyWorld() {
        if (lobbySpawn != null && lobbySpawn.getWorld() != null) {
            return lobbySpawn.getWorld();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public void shutdown() {
        bypassPlayers.clear();
        itemManager.shutdown();
    }

    public LobbyItemManager getItemManager() {
        return itemManager;
    }

    public enum PreparationCause {
        JOIN,
        RESPAWN,
        COMMAND
    }
}
