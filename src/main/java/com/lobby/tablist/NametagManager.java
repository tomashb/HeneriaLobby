package com.lobby.tablist;

import com.lobby.LobbyPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages player nametag formatting by creating and assigning scoreboard teams
 * based on LuckPerms groups. This manager cooperates with the tablist manager
 * to ensure prefixes and colors are consistent both in the player list and
 * above player heads.
 */
public final class NametagManager {

    private static final String TEAM_PREFIX = "nt";
    private static final int TEAM_NAME_MAX_LENGTH = 16;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private static final Map<ChatColor, int[]> BASE_CHAT_COLORS = createBaseChatColors();

    private final LobbyPlugin plugin;
    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;
    private final Map<String, TeamTemplate> templates = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();

    private volatile TeamTemplate defaultTemplate;

    public NametagManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        this.queryOptions = luckPerms != null ? luckPerms.getContextManager().getStaticQueryOptions() : null;
        reload();
    }

    public void reload() {
        templates.clear();
        if (luckPerms == null) {
            defaultTemplate = createFallbackTemplate();
            return;
        }
        final Collection<Group> groups = luckPerms.getGroupManager().getLoadedGroups();
        if (groups.isEmpty()) {
            defaultTemplate = createFallbackTemplate();
            return;
        }
        final List<Group> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort((first, second) -> {
            final int secondWeight = second.getWeight().orElse(0);
            final int firstWeight = first.getWeight().orElse(0);
            final int comparison = Integer.compare(secondWeight, firstWeight);
            if (comparison != 0) {
                return comparison;
            }
            return first.getName().compareToIgnoreCase(second.getName());
        });
        final Map<String, TeamTemplate> resolvedTemplates = new HashMap<>();
        int order = 1;
        for (Group group : sortedGroups) {
            final TeamTemplate template = createTemplateForGroup(group, order++);
            resolvedTemplates.put(group.getName().toLowerCase(Locale.ROOT), template);
            if (defaultTemplate == null && "default".equalsIgnoreCase(group.getName())) {
                defaultTemplate = template;
            }
        }
        templates.putAll(resolvedTemplates);
        if (defaultTemplate == null) {
            defaultTemplate = createFallbackTemplate();
        }
        refreshAllViewers();
    }

    public void applyNametag(final Player player, final PlayerTablistData data) {
        if (player == null) {
            return;
        }
        final TeamTemplate template = resolveTemplate(data != null ? data.primaryGroup() : null);
        final UUID uuid = player.getUniqueId();
        final String previousTeam = playerTeams.put(uuid, template.key());
        final String entry = player.getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            final Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            if (previousTeam != null && !previousTeam.equals(template.key())) {
                removeEntryFromTeam(scoreboard, entry, previousTeam);
            }
            addEntryToTeam(scoreboard, entry, template);
        }
    }

    public void removePlayer(final Player player) {
        if (player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        final String teamKey = playerTeams.remove(uuid);
        if (teamKey == null) {
            return;
        }
        final String entry = player.getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            final Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            removeEntryFromTeam(scoreboard, entry, teamKey);
        }
    }

    public void clearAll() {
        playerTeams.clear();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            final Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            clearTeams(scoreboard);
        }
    }

    public void shutdown() {
        clearAll();
        templates.clear();
        defaultTemplate = null;
    }

    private void refreshAllViewers() {
        final Map<UUID, String> assignments = new HashMap<>(playerTeams);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            final Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            clearTeams(scoreboard);
            for (Player target : Bukkit.getOnlinePlayers()) {
                final String teamKey = assignments.get(target.getUniqueId());
                final TeamTemplate template = resolveTemplateByKey(teamKey);
                if (template == null) {
                    continue;
                }
                addEntryToTeam(scoreboard, target.getName(), template);
            }
        }
    }

    private void addEntryToTeam(final Scoreboard scoreboard, final String entry, final TeamTemplate template) {
        Team team = scoreboard.getTeam(template.key());
        if (team == null) {
            team = scoreboard.registerNewTeam(template.key());
        }
        template.apply(team);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void removeEntryFromTeam(final Scoreboard scoreboard, final String entry, final String teamKey) {
        final Team team = teamKey != null ? scoreboard.getTeam(teamKey) : null;
        if (team == null) {
            return;
        }
        if (team.hasEntry(entry)) {
            team.removeEntry(entry);
        }
        if (team.getEntries().isEmpty() && team.getName().startsWith(TEAM_PREFIX)) {
            team.unregister();
        }
    }

    private void clearTeams(final Scoreboard scoreboard) {
        final Set<Team> teamsToRemove = new HashSet<>();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                teamsToRemove.add(team);
            }
        }
        for (Team team : teamsToRemove) {
            team.unregister();
        }
    }

    private TeamTemplate resolveTemplate(final String primaryGroup) {
        if (primaryGroup != null && !primaryGroup.isBlank()) {
            final TeamTemplate template = templates.get(primaryGroup.toLowerCase(Locale.ROOT));
            if (template != null) {
                return template;
            }
        }
        if (defaultTemplate == null) {
            defaultTemplate = createFallbackTemplate();
        }
        return defaultTemplate;
    }

    private TeamTemplate resolveTemplateByKey(final String key) {
        if (key == null) {
            return null;
        }
        for (TeamTemplate template : templates.values()) {
            if (template.key().equals(key)) {
                return template;
            }
        }
        if (defaultTemplate != null && defaultTemplate.key().equals(key)) {
            return defaultTemplate;
        }
        return null;
    }

    private TeamTemplate createTemplateForGroup(final Group group, final int order) {
        final String rawPrefix = resolveGroupPrefix(group);
        final String prefix = rawPrefix.isBlank() ? "" : appendSpace(rawPrefix);
        final ChatColor color = resolveGroupColor(group, rawPrefix);
        final String teamKey = createTeamKey(order, group.getName());
        return new TeamTemplate(teamKey, prefix, color);
    }

    private String resolveGroupPrefix(final Group group) {
        if (group == null || queryOptions == null) {
            return "";
        }
        final CachedMetaData metaData = group.getCachedData().getMetaData(queryOptions);
        if (metaData == null) {
            return "";
        }
        final String prefix = metaData.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    private ChatColor resolveGroupColor(final Group group, final String prefix) {
        ChatColor color = extractColorFromPrefix(prefix);
        if (group == null || queryOptions == null) {
            return color;
        }
        final CachedMetaData metaData = group.getCachedData().getMetaData(queryOptions);
        if (metaData == null) {
            return color;
        }
        final String metaColor = metaData.getMetaValue("color");
        final ChatColor resolved = parseColor(metaColor);
        return resolved != null ? resolved : color;
    }

    private ChatColor parseColor(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            final ChatColor hexColor = parseHexColor(trimmed);
            if (hexColor != null) {
                return hexColor;
            }
        }
        if (trimmed.length() == 2 && trimmed.charAt(0) == ChatColor.COLOR_CHAR) {
            final ChatColor color = ChatColor.getByChar(trimmed.charAt(1));
            if (color != null && color.isColor()) {
                return color;
            }
        }
        try {
            final ChatColor color = ChatColor.valueOf(trimmed.toUpperCase(Locale.ROOT));
            return color.isColor() ? color : null;
        } catch (final IllegalArgumentException ignored) {
            // Fallback handled by caller.
        }
        if (trimmed.length() == 2 && trimmed.charAt(0) == '&') {
            final ChatColor color = ChatColor.getByChar(trimmed.charAt(1));
            if (color != null && color.isColor()) {
                return color;
            }
        }
        return null;
    }

    private ChatColor parseHexColor(final String value) {
        if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
            return null;
        }
        final String hex = value.charAt(0) == '#' ? value.substring(1) : value;
        try {
            final int rgb = Integer.parseInt(hex, 16);
            final int red = (rgb >> 16) & 0xFF;
            final int green = (rgb >> 8) & 0xFF;
            final int blue = rgb & 0xFF;
            ChatColor nearest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Map.Entry<ChatColor, int[]> entry : BASE_CHAT_COLORS.entrySet()) {
                final int[] components = entry.getValue();
                final int distance = computeDistance(red, green, blue, components[0], components[1], components[2]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = entry.getKey();
                }
            }
            return nearest;
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private int computeDistance(final int red, final int green, final int blue,
                                final int otherRed, final int otherGreen, final int otherBlue) {
        final int diffRed = red - otherRed;
        final int diffGreen = green - otherGreen;
        final int diffBlue = blue - otherBlue;
        return diffRed * diffRed + diffGreen * diffGreen + diffBlue * diffBlue;
    }

    private ChatColor extractColorFromPrefix(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return ChatColor.WHITE;
        }
        for (int index = prefix.length() - 1; index >= 0; index--) {
            if (prefix.charAt(index) != ChatColor.COLOR_CHAR || index + 1 >= prefix.length()) {
                continue;
            }
            final char code = Character.toLowerCase(prefix.charAt(index + 1));
            final ChatColor color = ChatColor.getByChar(code);
            if (color != null && color.isColor()) {
                return color;
            }
        }
        return ChatColor.WHITE;
    }

    private TeamTemplate createFallbackTemplate() {
        final String key = createTeamKey(999, "default");
        final String prefix = appendSpace(ChatColor.GRAY + "Joueur");
        return new TeamTemplate(key, prefix, ChatColor.WHITE);
    }

    private String createTeamKey(final int order, final String groupName) {
        final String number = String.format("%03d", Math.max(0, Math.min(order, 999)));
        final String sanitized = sanitizeGroupName(groupName);
        final int remaining = TEAM_NAME_MAX_LENGTH - TEAM_PREFIX.length() - number.length();
        final String trimmed = sanitized.length() > remaining ? sanitized.substring(0, remaining) : sanitized;
        return TEAM_PREFIX + number + trimmed;
    }

    private String sanitizeGroupName(final String input) {
        if (input == null || input.isBlank()) {
            return "grp";
        }
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            final char character = Character.toLowerCase(input.charAt(index));
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                builder.append(character);
            }
        }
        if (builder.length() == 0) {
            return "grp";
        }
        return builder.toString();
    }

    private String appendSpace(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith(" ") ? value : value + ' ';
    }

    private static Map<ChatColor, int[]> createBaseChatColors() {
        final EnumMap<ChatColor, int[]> colors = new EnumMap<>(ChatColor.class);
        registerColor(colors, ChatColor.BLACK, 0x00, 0x00, 0x00);
        registerColor(colors, ChatColor.DARK_BLUE, 0x00, 0x00, 0xAA);
        registerColor(colors, ChatColor.DARK_GREEN, 0x00, 0xAA, 0x00);
        registerColor(colors, ChatColor.DARK_AQUA, 0x00, 0xAA, 0xAA);
        registerColor(colors, ChatColor.DARK_RED, 0xAA, 0x00, 0x00);
        registerColor(colors, ChatColor.DARK_PURPLE, 0xAA, 0x00, 0xAA);
        registerColor(colors, ChatColor.GOLD, 0xFF, 0xAA, 0x00);
        registerColor(colors, ChatColor.GRAY, 0xAA, 0xAA, 0xAA);
        registerColor(colors, ChatColor.DARK_GRAY, 0x55, 0x55, 0x55);
        registerColor(colors, ChatColor.BLUE, 0x55, 0x55, 0xFF);
        registerColor(colors, ChatColor.GREEN, 0x55, 0xFF, 0x55);
        registerColor(colors, ChatColor.AQUA, 0x55, 0xFF, 0xFF);
        registerColor(colors, ChatColor.RED, 0xFF, 0x55, 0x55);
        registerColor(colors, ChatColor.LIGHT_PURPLE, 0xFF, 0x55, 0xFF);
        registerColor(colors, ChatColor.YELLOW, 0xFF, 0xFF, 0x55);
        registerColor(colors, ChatColor.WHITE, 0xFF, 0xFF, 0xFF);
        return colors;
    }

    private static void registerColor(final EnumMap<ChatColor, int[]> colors,
                                      final ChatColor color,
                                      final int red, final int green, final int blue) {
        if (color != null && color.isColor()) {
            colors.put(color, new int[] { red, green, blue });
        }
    }

    private record TeamTemplate(String key, String prefix, ChatColor color) {

        private void apply(final Team team) {
            if (team == null) {
                return;
            }
            if (!Objects.equals(team.getPrefix(), prefix)) {
                team.setPrefix(prefix);
            }
            team.setSuffix("");
            if (color != null) {
                team.setColor(color);
            }
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
    }
}
