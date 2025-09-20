package com.lobby.social.clans;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class ClanRank {

    private final String name;
    private final String displayName;
    private final int priority;
    private final EnumSet<ClanPermission> permissions;

    public ClanRank(final String name, final int priority, final Set<ClanPermission> permissions) {
        this(name, name, priority, permissions);
    }

    public ClanRank(final String name, final String displayName, final int priority, final Set<ClanPermission> permissions) {
        this.name = Objects.requireNonNull(name, "name");
        final String normalizedDisplayName = displayName == null ? null : displayName.trim();
        this.displayName = (normalizedDisplayName == null || normalizedDisplayName.isEmpty()) ? this.name : normalizedDisplayName;
        this.priority = priority;
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = EnumSet.noneOf(ClanPermission.class);
        } else {
            this.permissions = EnumSet.copyOf(permissions);
        }
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPriority() {
        return priority;
    }

    public Set<ClanPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(final ClanPermission permission) {
        return permissions.contains(permission);
    }
}
