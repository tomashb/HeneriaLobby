package com.lobby.social.groups;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Group {

    private int id;
    private final UUID leaderUUID;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> moderators = new HashSet<>();
    private String name;
    private int maxSize = 8;
    private long createdAt = System.currentTimeMillis();

    public Group(final UUID leaderUUID) {
        this.leaderUUID = leaderUUID;
        this.members.add(leaderUUID);
    }

    public Group(final int id, final UUID leaderUUID, final String name, final int maxSize, final long createdAt) {
        this.id = id;
        this.leaderUUID = leaderUUID;
        this.name = name;
        this.maxSize = maxSize;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public UUID getLeaderUUID() {
        return leaderUUID;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(final UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(final UUID uuid) {
        members.remove(uuid);
        moderators.remove(uuid);
    }

    public boolean isLeader(final UUID uuid) {
        return leaderUUID.equals(uuid);
    }

    public boolean isModerator(final UUID uuid) {
        return moderators.contains(uuid);
    }

    public void addModerator(final UUID uuid) {
        if (!uuid.equals(leaderUUID)) {
            moderators.add(uuid);
        }
    }

    public void removeModerator(final UUID uuid) {
        moderators.remove(uuid);
    }

    public boolean canInvite(final UUID uuid) {
        return isLeader(uuid) || isModerator(uuid);
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public int getSize() {
        return members.size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(final int maxSize) {
        this.maxSize = maxSize;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final long createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return name != null ? name : "Groupe de " + leaderUUID.toString().substring(0, 8);
    }
}
