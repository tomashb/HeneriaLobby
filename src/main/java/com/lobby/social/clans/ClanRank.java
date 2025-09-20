package com.lobby.social.clans;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class ClanRank {

    private final String name;
    private final int priority;
    private final EnumSet<ClanPermission> permissions;

    public ClanRank(final String name, final int priority, final Set<ClanPermission> permissions) {
        this.name = name;
        this.priority = priority;
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(ClanPermission.class) : EnumSet.copyOf(permissions);
    }

    public String getName() {
        return name;
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
