package com.sinestroboss.entity;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Movimiento en vuelo: mantiene distancia en fases 1-2, ataca de cerca en fase 3. */
public class SinestroCombatGoal extends Goal {
    private final SinestroEntity boss;
    private int meleeCooldown = 0;
    private int strafeTicks = 0;
    private int strafeDir = 1;

    public SinestroCombatGoal(SinestroEntity boss) {
        this.boss = boss;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();
        if (target == null) {
            return;
        }
        boss.getLookControl().lookAt(target, 30.0f, 30.0f);

        int phase = boss.getPhase();
        double dist = boss.distanceTo(target);
        double desired = phase >= 3 ? 1.8 : 8.0;
        double targetY = target.getY() + (phase >= 3 ? 0.3 : 2.5);

        Vec3d flat = new Vec3d(target.getX() - boss.getX(), 0, target.getZ() - boss.getZ());
        flat = flat.lengthSquared() > 1.0E-4 ? flat.normalize() : Vec3d.ZERO;

        if (--strafeTicks <= 0) {
            strafeDir = -strafeDir;
            strafeTicks = 40 + boss.getRandom().nextInt(40);
        }

        double destX;
        double destZ;
        if (dist > desired + 2.0) {
            destX = target.getX();
            destZ = target.getZ();
        } else if (dist < desired - 2.0 && phase < 3) {
            destX = boss.getX() - flat.x * 4.0;
            destZ = boss.getZ() - flat.z * 4.0;
        } else {
            destX = boss.getX() + (-flat.z * strafeDir) * 3.0;
            destZ = boss.getZ() + (flat.x * strafeDir) * 3.0;
        }
        boss.getMoveControl().moveTo(destX, targetY, destZ, 1.0);

        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (dist <= 3.0 && meleeCooldown <= 0) {
            boss.swingHand(Hand.MAIN_HAND);
            boss.tryAttack(target);
            boss.yellowBurst(target.getX(), target.getBodyY(0.5), target.getZ(), 12);
            meleeCooldown = 20;
        }
    }
}
