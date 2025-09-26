package com.lobby.friends;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of every placeholder used by the friends menus. The record holds all
 * numeric and textual values that can be interpolated inside menu items.
 */
public record FriendsPlaceholderData(
        int amisEnLigne,
        int amisHorsLigne,
        int totalAmis,
        int limiteAmis,
        int demandesEnvoyees,
        int demandesEnAttente,
        int nouvellesDemandes,
        int totalDemandes,
        int joueursBloques,
        String demandesAuto,
        String notifications,
        String visibilite,
        int amisFavoris,
        int favorisEnLigne,
        String tempsTotal,
        int messagesTotal,
        String amiAncien) {

    private static final FriendsPlaceholderData EMPTY = new FriendsPlaceholderData(
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            "Désactivé", "Aucune", "Public",
            0, 0, "0h 0m", 0, "-"
    );

    public FriendsPlaceholderData {
        demandesAuto = normalizeText(demandesAuto, "Désactivé");
        notifications = normalizeText(notifications, "Aucune");
        visibilite = normalizeText(visibilite, "Public");
        tempsTotal = normalizeText(tempsTotal, "0h 0m");
        amiAncien = normalizeText(amiAncien, "-");
    }

    private static String normalizeText(final String input, final String defaultValue) {
        if (input == null || input.isBlank()) {
            return defaultValue;
        }
        return input;
    }

    public static FriendsPlaceholderData empty() {
        return EMPTY;
    }

    /**
     * Builds a mutable map containing every placeholder token and its
     * corresponding formatted value. Numeric values are formatted using the
     * default locale without grouping to ensure deterministic replacements.
     *
     * @return map of placeholder tokens without braces to their value
     */
    public Map<String, String> toPlaceholderMap() {
        final Map<String, String> map = new HashMap<>();
        map.put("amis_en_ligne", Integer.toString(Math.max(0, amisEnLigne())));
        map.put("amis_hors_ligne", Integer.toString(Math.max(0, amisHorsLigne())));
        map.put("total_amis", Integer.toString(Math.max(0, totalAmis())));
        map.put("limite_amis", Integer.toString(Math.max(0, limiteAmis())));
        map.put("demandes_envoyees", Integer.toString(Math.max(0, demandesEnvoyees())));
        map.put("demandes_en_attente", Integer.toString(Math.max(0, demandesEnAttente())));
        map.put("nouvelles_demandes", Integer.toString(Math.max(0, nouvellesDemandes())));
        map.put("total_demandes", Integer.toString(Math.max(0, totalDemandes())));
        map.put("joueurs_bloques", Integer.toString(Math.max(0, joueursBloques())));
        map.put("demandes_auto", Objects.requireNonNullElse(demandesAuto(), "Désactivé"));
        map.put("notifications", Objects.requireNonNullElse(notifications(), "Aucune"));
        map.put("visibilite", Objects.requireNonNullElse(visibilite(), "Public"));
        map.put("amis_favoris", Integer.toString(Math.max(0, amisFavoris())));
        map.put("favoris_en_ligne", Integer.toString(Math.max(0, favorisEnLigne())));
        map.put("temps_total", Objects.requireNonNullElse(tempsTotal(), "0h 0m"));
        map.put("messages_total", Integer.toString(Math.max(0, messagesTotal())));
        map.put("ami_ancien", Objects.requireNonNullElse(amiAncien(), "-"));
        return map;
    }
}

