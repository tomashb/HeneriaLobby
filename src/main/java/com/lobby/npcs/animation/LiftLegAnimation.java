package com.lobby.npcs.animation;

import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

class LiftLegAnimation extends BukkitRunnable {

    private final ArmorStand armorStand;
    private final boolean isRightLeg;
    private int step = 0;
    private static final int MAX_STEPS = 10;
    private static final double MAX_ANGLE_X = -45.0;

    LiftLegAnimation(final ArmorStand armorStand, final boolean isRightLeg) {
        this.armorStand = armorStand;
        this.isRightLeg = isRightLeg;
    }

    @Override
    public void run() {
        if (armorStand == null || armorStand.isDead()) {
            cancel();
            return;
        }

        if (step > MAX_STEPS) {
            cancel();
            return;
        }

        final double angle = (MAX_ANGLE_X / MAX_STEPS) * step;
        final EulerAngle legPose = new EulerAngle(Math.toRadians(angle), 0, 0);

        if (isRightLeg) {
            armorStand.setRightLegPose(legPose);
        } else {
            armorStand.setLeftLegPose(legPose);
        }

        step++;
    }
}
