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
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

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
        armorStand = world.spawn(location, ArmorStand.class);

        setupArmorStand();
        setHeadTexture();
        setDefaultEquipment();
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

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    private void setupArmorStand() {
        if (armorStand == null) {
            return;
        }

        armorStand.setVisible(true);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        armorStand.setBasePlate(false);
        armorStand.setArms(true);
        armorStand.setMarker(false);
        armorStand.setSmall(false);
        armorStand.setPersistent(true);
        armorStand.setRemoveWhenFarAway(false);
        armorStand.setCollidable(false);
        armorStand.setAI(false);
        final String customName = (data.displayName() == null || data.displayName().isEmpty())
                ? data.name() : data.displayName();
        armorStand.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(customName));
        armorStand.setCustomNameVisible(true);

        final NamespacedKey key = manager.getNpcKey();
        if (key != null) {
            armorStand.getPersistentDataContainer().set(key, PersistentDataType.STRING, data.name());
        }
    }

    private void setDefaultEquipment() {
        if (armorStand == null) {
            return;
        }

        final var equipment = armorStand.getEquipment();
        if (equipment == null) {
            return;
        }

        if (equipment.getChestplate() == null || equipment.getChestplate().getType() == Material.AIR) {
            equipment.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        }
        if (equipment.getLeggings() == null || equipment.getLeggings().getType() == Material.AIR) {
            equipment.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        }
        if (equipment.getBoots() == null || equipment.getBoots().getType() == Material.AIR) {
            equipment.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        }
        if (equipment.getItemInMainHand() == null || equipment.getItemInMainHand().getType() == Material.AIR) {
            equipment.setItemInMainHand(new ItemStack(Material.STICK));
        }
    }

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private ItemStack createCustomHead(final String texture) {
        final String trimmed = texture.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
                final ItemStack head = getHeadFromDatabase(trimmed.substring(4));
                if (head != null) {
                    return head;
                }
                LogUtils.warning(manager.getPlugin(), "HeadDatabase not available, using default head for NPC '" + data.name() + "'.");
                return getDefaultHead();
            }
            if (UUID_PATTERN.matcher(trimmed).matches()) {
                return getHeadFromPlayer(trimmed);
            }
            if (trimmed.length() > 50) {
                return getHeadFromBase64(trimmed);
            }
            return getHeadFromPlayer(trimmed);
        } catch (final Exception exception) {
            LogUtils.warning(manager.getPlugin(), "Failed to create custom head for NPC '" + data.name() + "': " + exception.getMessage());
            return getDefaultHead();
        }
    }

    private ItemStack getHeadFromDatabase(final String id) {
        final Plugin headDatabase = Bukkit.getPluginManager().getPlugin("HeadDatabase");
        if (headDatabase == null || !headDatabase.isEnabled()) {
            LogUtils.warning(manager.getPlugin(), "HeadDatabase plugin not found for NPC head " + id);
            return null;
        }
        try {
            final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object apiInstance;
            try {
                final Method getApiMethod = apiClass.getMethod("getAPI");
                apiInstance = getApiMethod.invoke(null);
            } catch (final NoSuchMethodException ignored) {
                final Constructor<?> constructor = apiClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                apiInstance = constructor.newInstance();
            }
            final Method method = apiClass.getMethod("getItemHead", String.class);
            final Object result = method.invoke(apiInstance, id);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (final ClassNotFoundException exception) {
            LogUtils.warning(manager.getPlugin(), "HeadDatabase plugin classes not found for NPC head " + id);
        } catch (final ReflectiveOperationException exception) {
            LogUtils.warning(manager.getPlugin(), "Failed to fetch head from HeadDatabase: " + exception.getMessage());
        }
        return null;
    }

    private ItemStack getHeadFromPlayer(final String identifier) {
        final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) {
            return getDefaultHead();
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
            return getDefaultHead();
        }
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack getDefaultHead() {
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private ItemStack getHeadFromBase64(final String base64) {
        LogUtils.warning(manager.getPlugin(), "Base64 textures are not supported yet for NPC '" + data.name() + "'.");
        return getDefaultHead();
    }
}
