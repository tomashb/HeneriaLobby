package com.lobby.friends.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a pending friend request entry.
 */
public class FriendRequest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String senderUuid;
    private final String receiverUuid;
    private final String message;
    private final Timestamp requestDate;

    public FriendRequest(final String senderUuid,
                         final String receiverUuid,
                         final String message,
                         final Timestamp requestDate) {
        this.senderUuid = senderUuid;
        this.receiverUuid = receiverUuid;
        this.message = message;
        this.requestDate = requestDate;
    }

    public String getSenderUuid() {
        return senderUuid;
    }

    public String getReceiverUuid() {
        return receiverUuid;
    }

    public String getMessage() {
        return message;
    }

    public Timestamp getRequestDate() {
        return requestDate;
    }

    public String getSenderName() {
        final Player online = getSenderPlayer();
        if (online != null) {
            return online.getName();
        }
        try {
            final OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(senderUuid));
            final String name = offline.getName();
            return name != null ? name : senderUuid.substring(0, Math.min(senderUuid.length(), 8));
        } catch (IllegalArgumentException ignored) {
            return senderUuid;
        }
    }

    public Player getSenderPlayer() {
        try {
            return Bukkit.getPlayer(UUID.fromString(senderUuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isSenderOnline() {
        return getSenderPlayer() != null;
    }

    public String getFormattedDate() {
        if (requestDate == null) {
            return "--/--/---- --:--";
        }
        final LocalDateTime dateTime = requestDate.toLocalDateTime();
        return DATE_FORMAT.format(dateTime);
    }

    public String getRelativeDate() {
        if (requestDate == null) {
            return "Inconnu";
        }
        final long diffMinutes = Math.max(0, (System.currentTimeMillis() - requestDate.getTime()) / (1000 * 60));
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

    public String getDisplayMessage() {
        if (message == null || message.trim().isEmpty()) {
            return "Aucun message";
        }
        return message.length() > 50 ? message.substring(0, 47) + "..." : message;
    }
}
