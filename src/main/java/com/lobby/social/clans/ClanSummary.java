package com.lobby.social.clans;

import java.util.UUID;

public record ClanSummary(int id,
                          String name,
                          String tag,
                          UUID leaderUuid,
                          String description,
                          int level,
                          int members,
                          int maxMembers) {
}
