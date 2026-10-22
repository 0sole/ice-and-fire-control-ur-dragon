package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class DragonFollowLogic {
    private DragonFollowLogic() {
    }

    public static float followDistance(DragonBaseEntity dragon) {
        return Mth.clamp(4F + dragon.getBbWidth() * 1.2F, 6F, 24F);
    }

    public static float horizontalDistance(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

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

    public static Vec3 flightTarget(DragonBaseEntity dragon) {
        if (!Config.IMPROVED_FOLLOW.get() || dragon.getControllingPassenger() != null) return null;

        LivingEntity pickup = DragonCarry.getPickupTarget(dragon);
        if (pickup != null) return new Vec3(pickup.getX(), pickup.getY() + pickup.getBbHeight() + 1.0, pickup.getZ());

        if (dragon.getTarget() != null && dragon.getTarget().isAlive()) return null;

        Entity carried = DragonCarry.getCarried(dragon);
        double extraHeight = carried == null ? 0 : carried.getBbHeight() + 2;

        DragonOrders.GuardArea guard = DragonOrders.getGuard(dragon);
        if (guard != null && guard.dimension().equals(dragon.level().dimension().location().toString())) {
            return guardFlightTarget(dragon, guard, extraHeight);
        }

        if (dragon.getCommand() != 2) return null;
        LivingEntity owner = dragon.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != dragon.level()) return null;

        float keep = followDistance(dragon);
        float horizontal = horizontalDistance(dragon, owner);
        boolean stayUp = carried != null || owner.isFallFlying()
                || !owner.onGround() && owner.getY() - groundY(dragon, owner.getX(), owner.getZ()) > 4;
        double cruiseY = Math.max(owner.getY() + 4 + dragon.getDragonStage(), groundY(dragon, dragon.getX(), dragon.getZ()) + 6) + extraHeight;

        if (horizontal > keep) {
            Vec3 near = pointNearOwner(dragon, owner, keep * 0.8F);
            return new Vec3(near.x, cruiseY, near.z);
        }
        if (stayUp) return new Vec3(dragon.getX(), cruiseY, dragon.getZ());

        double ground = groundY(dragon, dragon.getX(), dragon.getZ());
        if (dragon.onGround() || dragon.getY() - ground < 2.5) {
            dragon.setFlying(false);
            dragon.setHovering(false);
        }
        return new Vec3(dragon.getX(), ground, dragon.getZ());
    }

    private static Vec3 guardFlightTarget(DragonBaseEntity dragon, DragonOrders.GuardArea guard, double extraHeight) {
        BlockPos center = guard.center();
        double y = groundY(dragon, center.getX(), center.getZ()) + 10 + dragon.getDragonStage() * 2 + extraHeight;
        double dx = dragon.getX() - (center.getX() + 0.5);
        double dz = dragon.getZ() - (center.getZ() + 0.5);
        if (dx * dx + dz * dz > guard.radius() * guard.radius()) {
            return new Vec3(center.getX() + 0.5, y, center.getZ() + 0.5);
        }
        double angle = (dragon.level().getGameTime() + dragon.getId() * 37L) / 160.0;
        double r = guard.radius() * 0.5;
        return new Vec3(center.getX() + 0.5 + Math.cos(angle) * r, y, center.getZ() + 0.5 + Math.sin(angle) * r);
    }
}
