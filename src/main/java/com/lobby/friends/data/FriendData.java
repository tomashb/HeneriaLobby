package com.lobby.friends.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a friend entry stored in the database and exposed to the menus.
 */
public class FriendData {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String uuid;
    private final Timestamp friendshipDate;
    private boolean favorite;
    private int messagesExchanged;
    private long timeTogether;
    private Timestamp lastInteraction;

    public FriendData(final String uuid,
                      final Timestamp friendshipDate,
                      final boolean favorite,
                      final int messagesExchanged,
                      final long timeTogether,
                      final Timestamp lastInteraction) {
        this.uuid = uuid;
        this.friendshipDate = friendshipDate;
        this.favorite = favorite;
        this.messagesExchanged = messagesExchanged;
        this.timeTogether = timeTogether;
        this.lastInteraction = lastInteraction != null ? lastInteraction : new Timestamp(System.currentTimeMillis());
    }

    public String getUuid() {
        return uuid;
    }

    public Timestamp getFriendshipDate() {
        return friendshipDate;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public int getMessagesExchanged() {
        return messagesExchanged;
    }

    public long getTimeTogether() {
        return timeTogether;
    }

    public Timestamp getLastInteraction() {
        return lastInteraction;
    }

    public void setFavorite(final boolean favorite) {
        this.favorite = favorite;
    }

    public void setMessagesExchanged(final int messagesExchanged) {
        this.messagesExchanged = messagesExchanged;
    }

    public void setTimeTogether(final long timeTogether) {
        this.timeTogether = timeTogether;
    }

    public void setLastInteraction(final Timestamp lastInteraction) {
        this.lastInteraction = lastInteraction;
    }

    public Player getPlayer() {
        try {
            return Bukkit.getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String getPlayerName() {
        final Player online = getPlayer();
        if (online != null) {
            return online.getName();
        }
        try {
            final OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            final String name = offline.getName();
            return name != null ? name : uuid.substring(0, Math.min(uuid.length(), 8));
        } catch (IllegalArgumentException ignored) {
            return uuid;
        }
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public String getFormattedFriendshipDate() {
        if (friendshipDate == null) {
            return "--/--/----";
        }
        final LocalDateTime dateTime = friendshipDate.toLocalDateTime();
        return DATE_FORMAT.format(dateTime);
    }

    public String getFormattedTimeTogether() {
        final long hours = timeTogether / 3600;
        final long minutes = (timeTogether % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public String getRelativeLastInteraction() {
        if (lastInteraction == null) {
            return "Inconnu";
        }
        final long diffMinutes = Math.max(0, (System.currentTimeMillis() - lastInteraction.getTime()) / (1000 * 60));
        if (diffMinutes < 1) {
            return "À l'instant";
        }
        if (diffMinutes < 60) {
            return "Il y a " + diffMinutes + " minute(s)";
        }
        if (diffMinutes < 1440) {
            return "Il y a " + (diffMinutes / 60) + " heure(s)";
        }
        return "Il y a " + (diffMinutes / 1440) + " jour(s)";
    }

    public String getStatusColor() {
        if (isOnline()) {
            return favorite ? "§e⭐ §a" : "§a";
        }
        return favorite ? "§e⭐ §7" : "§7";
    }

    public String getStatusIndicator() {
        return isOnline() ? "§2●" : "§8●";
    }
}
