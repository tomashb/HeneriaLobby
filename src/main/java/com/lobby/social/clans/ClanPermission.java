package com.lobby.social.clans;

public enum ClanPermission {

    // Base permissions
    CHAT_CLAN,
    VIEW_MEMBER_LIST,

    // Moderator permissions
    INVITE_MEMBERS,
    KICK_MEMBERS,

    // Co-leader permissions
    PROMOTE_MEMBERS,
    DEMOTE_MEMBERS,
    BAN_MEMBERS,
    MANAGE_CLAN_INFO,

    // Management permissions
    MANAGE_PERMISSIONS,

    // Leader exclusive permissions
    TRANSFER_LEADERSHIP,
    DISBAND_CLAN
}
