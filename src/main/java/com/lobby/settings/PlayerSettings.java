package com.lobby.settings;

import java.util.Locale;

public class PlayerSettings {

    private boolean privateMessages = true;
    private GroupRequestSetting groupRequestSetting = GroupRequestSetting.EVERYONE;
    private VisibilitySetting visibilitySetting = VisibilitySetting.EVERYONE;
    private boolean uiSounds = true;
    private boolean particles = true;
    private boolean music = false;
    private boolean clanNotifications = true;
    private boolean systemNotifications = true;
    private String language = "fr";

    public PlayerSettings() {
    }

    public PlayerSettings(final boolean privateMessages,
                          final GroupRequestSetting groupRequestSetting,
                          final VisibilitySetting visibilitySetting,
                          final boolean uiSounds,
                          final boolean particles,
                          final boolean music,
                          final boolean clanNotifications,
                          final boolean systemNotifications,
                          final String language) {
        this.privateMessages = privateMessages;
        this.groupRequestSetting = groupRequestSetting == null ? GroupRequestSetting.EVERYONE : groupRequestSetting;
        this.visibilitySetting = visibilitySetting == null ? VisibilitySetting.EVERYONE : visibilitySetting;
        this.uiSounds = uiSounds;
        this.particles = particles;
        this.music = music;
        this.clanNotifications = clanNotifications;
        this.systemNotifications = systemNotifications;
        this.language = language == null || language.isBlank() ? "fr" : language.toLowerCase(Locale.ROOT);
    }

    public boolean isPrivateMessages() {
        return privateMessages;
    }

    public void setPrivateMessages(final boolean privateMessages) {
        this.privateMessages = privateMessages;
    }

    public GroupRequestSetting getGroupRequestSetting() {
        return groupRequestSetting;
    }

    public void setGroupRequestSetting(final GroupRequestSetting groupRequestSetting) {
        this.groupRequestSetting = groupRequestSetting == null
                ? GroupRequestSetting.EVERYONE
                : groupRequestSetting;
    }

    public VisibilitySetting getVisibilitySetting() {
        return visibilitySetting;
    }

    public void setVisibilitySetting(final VisibilitySetting visibilitySetting) {
        this.visibilitySetting = visibilitySetting == null
                ? VisibilitySetting.EVERYONE
                : visibilitySetting;
    }

    public boolean isUiSounds() {
        return uiSounds;
    }

    public void setUiSounds(final boolean uiSounds) {
        this.uiSounds = uiSounds;
    }

    public boolean isParticles() {
        return particles;
    }

    public void setParticles(final boolean particles) {
        this.particles = particles;
    }

    public boolean isMusic() {
        return music;
    }

    public void setMusic(final boolean music) {
        this.music = music;
    }

    public boolean isClanNotifications() {
        return clanNotifications;
    }

    public void setClanNotifications(final boolean clanNotifications) {
        this.clanNotifications = clanNotifications;
    }

    public boolean isSystemNotifications() {
        return systemNotifications;
    }

    public void setSystemNotifications(final boolean systemNotifications) {
        this.systemNotifications = systemNotifications;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(final String language) {
        this.language = language == null || language.isBlank()
                ? "fr"
                : language.toLowerCase(Locale.ROOT);
    }

    public String getPrivateMessagesDisplay() {
        return privateMessages ? "&aActivé" : "&cDésactivé";
    }

    public String getGroupRequestsDisplay() {
        return groupRequestSetting.getColoredDisplay();
    }

    public String getVisibilityDisplay() {
        return visibilitySetting.getColoredDisplay();
    }

    public String getUiSoundsDisplay() {
        return uiSounds ? "&aActivé" : "&cDésactivé";
    }

    public String getParticlesDisplay() {
        return particles ? "&aActivé" : "&cDésactivé";
    }

    public String getMusicDisplay() {
        return music ? "&aActivé" : "&cDésactivé";
    }

    public String getClanNotificationsDisplay() {
        return clanNotifications ? "&aActivé" : "&cDésactivé";
    }

    public String getSystemNotificationsDisplay() {
        return systemNotifications ? "&aActivé" : "&cDésactivé";
    }

    public String getLanguageFlag() {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "en" -> "🇬🇧";
            case "es" -> "🇪🇸";
            default -> "🇫🇷";
        };
    }

    public String getLanguageName() {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "en" -> "English";
            case "es" -> "Español";
            default -> "Français";
        };
    }

    public String getLanguageDisplay() {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "en" -> "&f🇬🇧 English";
            case "es" -> "&e🇪🇸 Español";
            default -> "&c🇫🇷 Français";
        };
    }

    public String getLanguageStatus(final String code) {
        if (code == null) {
            return "&7Disponible";
        }
        return language.equalsIgnoreCase(code) ? "&aSélectionné" : "&7Disponible";
    }
}

