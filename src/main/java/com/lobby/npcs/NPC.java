package com.lobby.npcs;

import com.lobby.data.NPCData;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class NPC {

    private final NPCData data;
    private final NPCManager manager;
    private ArmorStand armorStand;
    private boolean spawned;

    public NPC(final NPCData data, final NPCManager manager) {
        this.data = data;
        this.manager = manager;
    }

    public NPCData getData() {
        return data;
    }

    public boolean isSpawned() {
        return spawned && armorStand != null && !armorStand.isDead();
    }

    public void spawn() {
        if (isSpawned()) {
            return;
        }
        final World world = Bukkit.getWorld(data.world());
        if (world == null) {
            LogUtils.warning(manager.getPlugin(), "World not found for NPC '" + data.name() + "': " + data.world());
            return;
        }
        final Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());
        armorStand = world.spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            stand.setRemoveWhenFarAway(false);
            stand.setCollidable(false);
            final String displayName = data.displayName() == null ? data.name() : data.displayName();
            stand.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(displayName));
            stand.setCustomNameVisible(true);
            final NamespacedKey key = manager.getNpcKey();
            if (key != null) {
                stand.getPersistentDataContainer().set(key, PersistentDataType.STRING, data.name());
            }
        });
        setHeadTexture();
        spawned = true;
    }

    public void despawn() {
        if (armorStand != null) {
            armorStand.remove();
            armorStand = null;
        }
        spawned = false;
    }

    public void handleInteraction(final Player player, final ClickType clickType) {
        if (player == null || !isSpawned()) {
            return;
        }
        if (manager.isOnCooldown(player.getUniqueId(), data.name())) {
            MessageUtils.sendConfigMessage(player, "npc.interaction_cooldown");
            return;
        }
        if (manager.shouldLookAtPlayer()) {
            lookAt(player);
        }
        manager.getActionProcessor().processActions(data.actions(), player, this);
        final long duration = manager.getInteractionCooldownMillis();
        if (duration > 0) {
            manager.setCooldown(player.getUniqueId(), data.name(), duration);
        }
    }

    private void lookAt(final Player player) {
        if (armorStand == null) {
            return;
        }
        final Location npcLocation = armorStand.getLocation();
        final Location target = player.getEyeLocation();
        final Vector direction = target.toVector().subtract(npcLocation.toVector());
        final double distanceXZ = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        final float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        final float pitch = (float) Math.toDegrees(-Math.atan2(direction.getY(), distanceXZ));
        armorStand.setRotation(yaw, pitch);
    }

    private void setHeadTexture() {
        if (armorStand == null) {
            return;
        }
        final String texture = data.headTexture();
        if (texture == null || texture.isEmpty()) {
            return;
        }
        final ItemStack head = createCustomHead(texture);
        if (head == null) {
            return;
        }
        final var equipment = armorStand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(head);
        }
    }

    private ItemStack createCustomHead(final String texture) {
        final String trimmed = texture.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            return getHeadFromDatabase(trimmed.substring(4));
        }
        return getHeadFromPlayer(trimmed);
    }

    private ItemStack getHeadFromDatabase(final String id) {
        try {
            final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            final Constructor<?> constructor = apiClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            final Object apiInstance = constructor.newInstance();
            final Method method = apiClass.getMethod("getItemHead", String.class);
            final Object result = method.invoke(apiInstance, id);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (final ClassNotFoundException ignored) {
            LogUtils.warning(manager.getPlugin(), "HeadDatabase plugin not found for NPC head " + id);
        } catch (final ReflectiveOperationException exception) {
            LogUtils.warning(manager.getPlugin(), "Failed to fetch head from HeadDatabase: " + exception.getMessage());
        }
        return null;
    }

    private ItemStack getHeadFromPlayer(final String identifier) {
        final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) {
            return null;
        }
        try {
            final OfflinePlayer owner;
            if (identifier.contains("-")) {
                final UUID uuid = UUID.fromString(identifier);
                owner = Bukkit.getOfflinePlayer(uuid);
            } else {
                owner = Bukkit.getOfflinePlayer(identifier);
            }
            meta.setOwningPlayer(owner);
        } catch (final IllegalArgumentException exception) {
            LogUtils.warning(manager.getPlugin(), "Invalid head identifier for NPC '" + data.name() + "': " + identifier);
            return null;
        }
        skull.setItemMeta(meta);
        return skull;
    }
}
