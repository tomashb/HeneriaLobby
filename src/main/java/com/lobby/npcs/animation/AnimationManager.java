package com.lobby.npcs.animation;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AnimationManager {

    private final Map<UUID, BukkitRunnable> activeAnimations = new HashMap<>();
    private final LobbyPlugin plugin;

    public AnimationManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean playAnimation(final ArmorStand armorStand, final String animationType) {
        if (armorStand == null || armorStand.isDead()) {
            return false;
        }
        if (animationType == null || animationType.trim().isEmpty()) {
            return false;
        }

        final String normalized = animationType.trim().toUpperCase(Locale.ROOT);

        stopAnimation(armorStand);

        final BukkitRunnable animationTask;
        switch (normalized) {
            case "LEVER_BRAS_DROIT" -> animationTask = new LiftArmAnimation(armorStand, true);
            case "LEVER_JAMBE_GAUCHE" -> animationTask = new LiftLegAnimation(armorStand, false);
            case "MARCHER" -> animationTask = new WalkCycleAnimation(armorStand);
            default -> {
                if (plugin != null) {
                    plugin.getLogger().warning("Type d'animation inconnu : " + animationType);
                }
                return false;
            }
        }

        final long period = "MARCHER".equals(normalized) ? 3L : 5L;
        animationTask.runTaskTimer(plugin, 0L, period);
        activeAnimations.put(armorStand.getUniqueId(), animationTask);
        return true;
    }

    public void stopAnimation(final ArmorStand armorStand) {
        if (armorStand == null) {
            return;
        }
        final BukkitRunnable task = activeAnimations.remove(armorStand.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        resetPose(armorStand);
    }

    public void resetPose(final ArmorStand armorStand) {
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        armorStand.setHeadPose(EulerAngle.ZERO);
        armorStand.setBodyPose(EulerAngle.ZERO);
        armorStand.setLeftArmPose(EulerAngle.ZERO);
        armorStand.setRightArmPose(EulerAngle.ZERO);
        armorStand.setLeftLegPose(EulerAngle.ZERO);
        armorStand.setRightLegPose(EulerAngle.ZERO);
    }

    public void stopAll() {
        activeAnimations.forEach((uuid, task) -> {
            if (task != null) {
                task.cancel();
            }
            final var entity = Bukkit.getEntity(uuid);
            if (entity instanceof ArmorStand armorStand) {
                resetPose(armorStand);
            }
        });
        activeAnimations.clear();
    }
}
