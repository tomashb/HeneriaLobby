package com.lobby.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Utility methods to serialize and deserialize {@link ItemStack} instances to
 * and from Base64 strings so they can be stored in the database.
 */
public final class ItemSerializationUtils {

    private ItemSerializationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String serializeItem(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(byteStream)) {
            dataOutput.writeObject(item);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to serialize item stack", exception);
        }
    }

    public static ItemStack deserializeItem(final String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        final byte[] data = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(byteStream)) {
            final Object object = dataInput.readObject();
            if (object instanceof ItemStack item) {
                return item;
            }
        } catch (final IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize item stack", exception);
        }
        return null;
    }
}
