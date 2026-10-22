package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Shared math for following and guarding: how far a dragon should stay from you, where it should walk to,
 * and where it should fly to.
 */
public final class DragonFollowLogic {
    private DragonFollowLogic() {
    }

    /** Distance at which the dragon counts as "next to you". Bigger dragons keep more room. */
    public static float followDistance(DragonBaseEntity dragon) {
        return Mth.clamp(4F + dragon.getBbWidth() * 1.2F, 6F, 24F);
    }

    public static float horizontalDistance(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** A point near the owner on the dragon's side, so it never tries to stand on top of you. */
    public static Vec3 pointNearOwner(DragonBaseEntity dragon, LivingEntity owner, float distance) {
        double dx = dragon.getX() - owner.getX();
        double dz = dragon.getZ() - owner.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        return new Vec3(owner.getX() + dx / length * distance, owner.getY(), owner.getZ() + dz / length * distance);
    }

    private static double groundY(DragonBaseEntity dragon, double x, double z) {
        return dragon.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /**
     * Where a flying dragon should fly this tick, or null to let Ice and Fire decide.
     * Called from the flight manager mixin.
     */
    public static Vec3 flightTarget(DragonBaseEntity dragon) {
        if (!Config.IMPROVED_FOLLOW.get() || dragon.getControllingPassenger() != null) return null;
        if (dragon.getTarget() != null && dragon.getTarget().isAlive()) return null; // Ice and Fire's air attacks

        DragonOrders.GuardArea guard = DragonOrders.getGuard(dragon);
        if (guard != null && guard.dimension().equals(dragon.level().dimension().location().toString())) {
            return guardFlightTarget(dragon, guard);
        }

        if (dragon.getCommand() != 2) return null;
        LivingEntity owner = dragon.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != dragon.level()) return null;

        float keep = followDistance(dragon);
        float horizontal = horizontalDistance(dragon, owner);
        boolean ownerInAir = owner.isFallFlying() || !owner.onGround() && owner.getY() - groundY(dragon, owner.getX(), owner.getZ()) > 4;
        double cruiseY = Math.max(owner.getY() + 4 + dragon.getDragonStage(), groundY(dragon, dragon.getX(), dragon.getZ()) + 6);

        if (horizontal > keep) {
            Vec3 near = pointNearOwner(dragon, owner, keep * 0.8F);
            return new Vec3(near.x, cruiseY, near.z);
        }
        if (ownerInAir) {
            // Stay beside the owner in the air.
            return new Vec3(dragon.getX(), cruiseY, dragon.getZ());
        }
        // Close enough and the owner is on the ground: come down and land.
        double ground = groundY(dragon, dragon.getX(), dragon.getZ());
        if (dragon.onGround() || dragon.getY() - ground < 2.5) {
            dragon.setFlying(false);
            dragon.setHovering(false);
        }
        return new Vec3(dragon.getX(), ground, dragon.getZ());
    }

    private static Vec3 guardFlightTarget(DragonBaseEntity dragon, DragonOrders.GuardArea guard) {
        BlockPos center = guard.center();
        double y = groundY(dragon, center.getX(), center.getZ()) + 10 + dragon.getDragonStage() * 2;
        double dx = dragon.getX() - (center.getX() + 0.5);
        double dz = dragon.getZ() - (center.getZ() + 0.5);
        if (dx * dx + dz * dz > guard.radius() * guard.radius()) {
            return new Vec3(center.getX() + 0.5, y, center.getZ() + 0.5);
        }
        // Slowly circle over the area.
        double angle = (dragon.level().getGameTime() + dragon.getId() * 37L) / 160.0;
        double r = guard.radius() * 0.5;
        return new Vec3(center.getX() + 0.5 + Math.cos(angle) * r, y, center.getZ() + 0.5 + Math.sin(angle) * r);
    }
}
