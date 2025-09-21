package com.lobby.social.clans;

import org.bukkit.ChatColor;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum ClanRole {

    MEMBER(1, "Membre", ChatColor.GRAY,
            EnumSet.of(ClanPermission.CHAT_CLAN, ClanPermission.VIEW_MEMBER_LIST)),

    MODERATOR(2, "Modérateur", ChatColor.YELLOW,
            EnumSet.of(ClanPermission.CHAT_CLAN, ClanPermission.VIEW_MEMBER_LIST,
                    ClanPermission.INVITE_MEMBERS, ClanPermission.KICK_MEMBERS)),

    CO_LEADER(3, "Co-Leader", ChatColor.GOLD,
            EnumSet.of(ClanPermission.CHAT_CLAN, ClanPermission.VIEW_MEMBER_LIST,
                    ClanPermission.INVITE_MEMBERS, ClanPermission.KICK_MEMBERS,
                    ClanPermission.PROMOTE_MEMBERS, ClanPermission.DEMOTE_MEMBERS,
                    ClanPermission.BAN_MEMBERS, ClanPermission.MANAGE_CLAN_INFO)),

    LEADER(4, "Leader", ChatColor.RED, EnumSet.allOf(ClanPermission.class));

    private static final Map<String, ClanRole> LOOKUP = new ConcurrentHashMap<>();

    static {
        for (final ClanRole role : values()) {
            LOOKUP.put(normalize(role.name()), role);
            LOOKUP.put(normalize(role.displayName), role);
        }
    }

    private final int level;
    private final String displayName;
    private final ChatColor color;
    private final EnumSet<ClanPermission> permissions;

    ClanRole(final int level, final String displayName, final ChatColor color,
             final Set<ClanPermission> permissions) {
        this.level = level;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.color = Objects.requireNonNull(color, "color");
        this.permissions = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(ClanPermission.class)
                : EnumSet.copyOf(permissions);
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public EnumSet<ClanPermission> getPermissions() {
        return EnumSet.copyOf(permissions);
    }

    public boolean hasPermission(final ClanPermission permission) {
        return permission != null && (this == LEADER || permissions.contains(permission));
    }

    public ClanRole getNext() {
        return switch (this) {
            case MEMBER -> MODERATOR;
            case MODERATOR -> CO_LEADER;
            case CO_LEADER -> LEADER;
            case LEADER -> null;
        };
    }

    public ClanRole getPrevious() {
        return switch (this) {
            case MEMBER -> null;
            case MODERATOR -> MEMBER;
            case CO_LEADER -> MODERATOR;
            case LEADER -> CO_LEADER;
        };
    }

    public static ClanRole fromName(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String normalized = normalize(value);
        final ClanRole role = LOOKUP.get(normalized);
        if (role != null) {
            return role;
        }
        // Attempt lookup with spacing removed
        return LOOKUP.get(normalized.replace(" ", ""));
    }

    private static String normalize(final String input) {
        final String trimmed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return trimmed.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ');
    }
}

