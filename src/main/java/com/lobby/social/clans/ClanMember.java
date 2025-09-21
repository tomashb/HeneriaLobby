package com.lobby.social.clans;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class ClanMember {

    private final UUID uuid;
    private String rankName;
    private long joinedAt;
    private long totalContributions;
    private final EnumSet<ClanPermission> permissions = EnumSet.noneOf(ClanPermission.class);

    public ClanMember(final UUID uuid, final String rankName, final long joinedAt, final long totalContributions) {
        this.uuid = uuid;
        this.rankName = rankName;
        this.joinedAt = joinedAt;
        this.totalContributions = totalContributions;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getRankName() {
        return rankName;
    }

    public void setRankName(final String rankName) {
        this.rankName = rankName;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(final long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public long getTotalContributions() {
        return totalContributions;
    }

    public void addContribution(final long amount) {
        this.totalContributions += amount;
    }

    public Set<ClanPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void setPermissions(final Set<ClanPermission> updated) {
        permissions.clear();
        if (updated != null) {
            permissions.addAll(updated);
        }
    }

    public void togglePermission(final ClanPermission permission) {
        if (permission == null) {
            return;
        }
        if (permissions.contains(permission)) {
            permissions.remove(permission);
        } else {
            permissions.add(permission);
        }
    }
}
