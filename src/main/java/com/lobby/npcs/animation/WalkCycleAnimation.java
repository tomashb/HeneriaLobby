package com.lobby.npcs.animation;

import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

class WalkCycleAnimation extends BukkitRunnable {

    private final ArmorStand armorStand;
    private double time = 0;
    private static final double MAX_ANGLE_DEGREES = 45.0;

    WalkCycleAnimation(final ArmorStand armorStand) {
        this.armorStand = armorStand;
    }

    @Override
    public void run() {
        if (armorStand == null || armorStand.isDead()) {
            cancel();
            return;
        }

        time += 0.3;

        final double angleRad = Math.toRadians(MAX_ANGLE_DEGREES) * Math.sin(time);
        final double oppositeAngleRad = -angleRad;

        armorStand.setLeftLegPose(new EulerAngle(angleRad, 0, 0));
        armorStand.setRightArmPose(new EulerAngle(angleRad, 0, 0));

        armorStand.setRightLegPose(new EulerAngle(oppositeAngleRad, 0, 0));
        armorStand.setLeftArmPose(new EulerAngle(oppositeAngleRad, 0, 0));
    }
}
