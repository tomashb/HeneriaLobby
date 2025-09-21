package com.lobby.social.clans;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Clan {

    private int id;
    private final String name;
    private final String tag;
    private final UUID leaderUUID;
    private String description;
    private int maxMembers = 50;
    private int points = 0;
    private int level = 1;
    private long bankCoins = 0L;
    private final Map<UUID, ClanMember> members = new HashMap<>();
    private final Map<String, ClanRank> ranks = new HashMap<>();

    public Clan(final String name, final String tag, final UUID leaderUUID) {
        this.name = name;
        this.tag = tag;
        this.leaderUUID = leaderUUID;
    }

    public Clan(final int id, final String name, final String tag, final UUID leaderUUID) {
        this(name, tag, leaderUUID);
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeaderUUID() {
        return leaderUUID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(final int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(final int amount) {
        this.points += amount;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(final int level) {
        this.level = level;
    }

    public long getBankCoins() {
        return bankCoins;
    }

    public void setBankCoins(final long bankCoins) {
        this.bankCoins = bankCoins;
    }

    public void deposit(final long amount) {
        this.bankCoins += amount;
    }

    public void withdraw(final long amount) {
        this.bankCoins = Math.max(0L, this.bankCoins - amount);
    }

    public Map<UUID, ClanMember> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public Collection<ClanRank> getRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    public void addMember(final ClanMember member) {
        members.put(member.getUuid(), member);
    }

    public ClanMember getMember(final UUID uuid) {
        return members.get(uuid);
    }

    public void removeMember(final UUID uuid) {
        members.remove(uuid);
    }

    public void addRank(final ClanRank rank) {
        ranks.put(rank.getName(), rank);
    }

    public ClanRank getRank(final String name) {
        return ranks.get(name);
    }

    public boolean isLeader(final UUID uuid) {
        return leaderUUID.equals(uuid);
    }

    public boolean hasPermission(final UUID uuid, final ClanPermission permission) {
        if (isLeader(uuid)) {
            return true;
        }
        final ClanMember member = members.get(uuid);
        if (member == null) {
            return false;
        }
        final var customPermissions = member.getPermissions();
        if (!customPermissions.isEmpty()) {
            return customPermissions.contains(permission);
        }
        final ClanRank rank = ranks.get(member.getRankName());
        return rank != null && rank.hasPermission(permission);
    }

    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    public void levelUp() {
        final int requiredPoints = level * 1000;
        if (points >= requiredPoints) {
            level++;
            points -= requiredPoints;
            maxMembers += 5;
        }
    }
}
