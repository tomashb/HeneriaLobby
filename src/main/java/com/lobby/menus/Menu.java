package com.lobby.menus;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public interface Menu {

    void open(Player player);

    void handleClick(InventoryClickEvent event);

    Inventory getInventory();

    default List<String> getActionsForSlot(final int slot) {
        return Collections.emptyList();
    }

    default AsyncPreparation prepareAsync(final Player player) {
        return AsyncPreparation.EMPTY;
    }

    default void applyAsyncPreparation(final AsyncPreparationResult result) {
        // No-op by default.
    }

    record HeadRequest(String headId, Material fallback) {
        public HeadRequest {
            headId = headId == null ? "" : headId.trim();
            fallback = Objects.requireNonNullElse(fallback, Material.PLAYER_HEAD);
        }

        public boolean isValid() {
            return !headId.isEmpty();
        }
    }

    record AsyncPreparation(Set<HeadRequest> headRequests) {
        static final AsyncPreparation EMPTY = new AsyncPreparation(Set.of());

        public AsyncPreparation {
            headRequests = headRequests == null || headRequests.isEmpty() ? Set.of() : Set.copyOf(headRequests);
        }
    }

    record AsyncPreparationResult(Map<HeadRequest, ItemStack> preloadedHeads,
                                  Map<String, String> placeholderValues) {
        static final AsyncPreparationResult EMPTY = new AsyncPreparationResult(Map.of(), Map.of());

        public AsyncPreparationResult {
            preloadedHeads = preloadedHeads == null || preloadedHeads.isEmpty()
                    ? Map.of()
                    : Map.copyOf(preloadedHeads);
            placeholderValues = placeholderValues == null || placeholderValues.isEmpty()
                    ? Map.of()
                    : Map.copyOf(placeholderValues);
        }
    }
}
