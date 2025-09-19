package com.lobby.npcs.animation;

import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

class LiftArmAnimation extends BukkitRunnable {

    private final ArmorStand armorStand;
    private final boolean isRightArm;
    private int step = 0;
    private static final int MAX_STEPS = 10;
    private static final double MAX_ANGLE_X = -90.0;

    LiftArmAnimation(final ArmorStand armorStand, final boolean isRightArm) {
        this.armorStand = armorStand;
        this.isRightArm = isRightArm;
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
        final EulerAngle armPose = new EulerAngle(Math.toRadians(angle), 0, 0);

        if (isRightArm) {
            armorStand.setRightArmPose(armPose);
        } else {
            armorStand.setLeftArmPose(armPose);
        }

        step++;
    }
}
