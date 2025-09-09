package com.heneria.lobby.achievements;

import org.bukkit.Material;

/**
 * Represents a configurable achievement.
 */
public class Achievement {

    public enum ConditionType {
        PARKOUR_FINISH_TIME,
        ADD_FRIEND
    }

    private final String id;
    private final String name;
    private final String description;
    private final Material lockedIcon;
    private final Material unlockedIcon;
    private final ConditionType conditionType;
    private final double value;
    private final long rewardCoins;
    private final String rewardTitle;

    public Achievement(String id, String name, String description, Material lockedIcon, Material unlockedIcon,
                       ConditionType conditionType, double value, long rewardCoins, String rewardTitle) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.lockedIcon = lockedIcon;
        this.unlockedIcon = unlockedIcon;
        this.conditionType = conditionType;
        this.value = value;
        this.rewardCoins = rewardCoins;
        this.rewardTitle = rewardTitle;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Material getLockedIcon() {
        return lockedIcon;
    }

    public Material getUnlockedIcon() {
        return unlockedIcon;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public double getValue() {
        return value;
    }

    public long getRewardCoins() {
        return rewardCoins;
    }

    public String getRewardTitle() {
        return rewardTitle;
    }
}
