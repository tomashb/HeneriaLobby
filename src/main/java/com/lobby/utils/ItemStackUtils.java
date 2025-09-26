package com.lobby.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Utility helpers for {@link ItemStack} manipulation used across the lobby
 * plugin.
 */
public final class ItemStackUtils {

    private ItemStackUtils() {
    }

    /**
     * Applies a cosmetic glow effect to the provided item. The method first
     * tries to use {@link Enchantment#UNBREAKING}, which is the canonical name
     * since the 1.13+ Bukkit API, and falls back to the legacy "DURABILITY"
     * name if the server is running an older version. As a last resort it will
     * use the first available enchantment exposed by the server to guarantee
     * that an enchantment is applied. The enchantment is hidden from the item
     * lore by adding the {@link ItemFlag#HIDE_ENCHANTS} flag when a meta is
     * supplied.
     *
     * @param item the item that should receive the glow effect
     * @param meta the mutable item meta associated with {@code item}
     */
    public static void addGlowEffect(final ItemStack item, final ItemMeta meta) {
        if (item == null) {
            return;
        }

        boolean enchantmentApplied = tryAddEnchantment(item, Enchantment.UNBREAKING);
        if (!enchantmentApplied) {
            final Enchantment legacy = Enchantment.getByName("DURABILITY");
            enchantmentApplied = tryAddEnchantment(item, legacy);
        }

        if (!enchantmentApplied) {
            for (Enchantment fallback : Enchantment.values()) {
                if (tryAddEnchantment(item, fallback)) {
                    break;
                }
            }
        }

        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    private static boolean tryAddEnchantment(final ItemStack item, final Enchantment enchantment) {
        if (item == null || enchantment == null) {
            return false;
        }

        if (item.getEnchantmentLevel(enchantment) > 0) {
            return true;
        }

        try {
            item.addEnchantment(enchantment, 1);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
