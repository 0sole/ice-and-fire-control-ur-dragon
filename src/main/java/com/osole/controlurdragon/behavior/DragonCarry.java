package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonCarry {
    private static final int PICKUP_TIMEOUT = 600;
    private static final double MAX_ORDER_DISTANCE = 96;

    private static final Map<UUID, UUID> CARRYING = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CARRIED_BY = new ConcurrentHashMap<>();
    private static final Map<UUID, Pickup> PICKUPS = new ConcurrentHashMap<>();
    private static final int FALL_PROTECTION_TICKS = 40;
    private static final Map<UUID, Long> FALLING = new ConcurrentHashMap<>();

    private record Pickup(UUID target, long expires) {
    }

    public enum Result {
        GRABBED, ON_THE_WAY, TOO_FAR, CANNOT
    }

    private DragonCarry() {
    }

    public static boolean canCarry(DragonBaseEntity dragon, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == dragon) return false;
        if (entity instanceof DragonBaseEntity || entity.isSpectator() || entity.getVehicle() == dragon) return false;
        if (entity.getType().is(Tags.EntityTypes.BOSSES)) return false;
        UUID carrier = CARRIED_BY.get(entity.getUUID());
        if (carrier != null && !carrier.equals(dragon.getUUID())) return false;
        return living.getBbWidth() <= Math.max(1.5F, dragon.getBbWidth() * 0.75F)
                && living.getBbHeight() <= Math.max(2.5F, dragon.getBbHeight());
    }

    public static float reach(DragonBaseEntity dragon) {
        return Math.max(3F, dragon.getBbWidth() * 0.5F + 2.5F);
    }

    private static double distanceToFeet(DragonBaseEntity dragon, Entity entity) {
        double dx = dragon.getX() - entity.getX();
        double dy = dragon.getY() - (entity.getY() + entity.getBbHeight());
        double dz = dragon.getZ() - entity.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean isCarrying(DragonBaseEntity dragon) {
        return CARRYING.containsKey(dragon.getUUID());
    }

    public static boolean isCarried(Entity entity) {
        return CARRIED_BY.containsKey(entity.getUUID());
    }

    public static boolean isProtected(Entity entity) {
        if (entity instanceof Player) return false;
        return CARRIED_BY.containsKey(entity.getUUID()) || FALLING.containsKey(entity.getUUID());
    }

    public static Entity getCarried(DragonBaseEntity dragon) {
        UUID id = CARRYING.get(dragon.getUUID());
        return id != null && dragon.level() instanceof ServerLevel level ? level.getEntity(id) : null;
    }

    public static LivingEntity getPickupTarget(DragonBaseEntity dragon) {
        Pickup pickup = PICKUPS.get(dragon.getUUID());
        if (pickup == null || !(dragon.level() instanceof ServerLevel level)) return null;
        return level.getEntity(pickup.target()) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public static LivingEntity findBelow(DragonBaseEntity dragon) {
        float reach = reach(dragon);
        AABB box = dragon.getBoundingBox().expandTowards(0, -reach - 3, 0).inflate(reach * 0.5F, 0, reach * 0.5F);
        return dragon.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != dragon.getOwner() && canCarry(dragon, e)).stream()
                .min(Comparator.comparingDouble(e -> distanceToFeet(dragon, e))).orElse(null);
    }

    public static Result order(DragonBaseEntity dragon, LivingEntity target) {
        if (!canCarry(dragon, target) || target.level() != dragon.level()) return Result.CANNOT;
        release(dragon);
        if (distanceToFeet(dragon, target) <= reach(dragon)) {
            grab(dragon, target);
            return Result.GRABBED;
        }
        if (dragon.getControllingPassenger() != null || dragon.distanceTo(target) > MAX_ORDER_DISTANCE) return Result.TOO_FAR;
        if (!dragon.isFlying() && !dragon.isHovering() && !dragon.isAllowedToTriggerFlight()) return Result.CANNOT;
        DragonOrders.clearAll(dragon);
        dragon.setOrderedToSit(false);
        dragon.setInSittingPose(false);
        if (dragon.getCommand() == 1) dragon.setCommand(2);
        PICKUPS.put(dragon.getUUID(), new Pickup(target.getUUID(), dragon.level().getGameTime() + PICKUP_TIMEOUT));
        return Result.ON_THE_WAY;
    }

    private static void grab(DragonBaseEntity dragon, LivingEntity target) {
        PICKUPS.remove(dragon.getUUID());
        FALLING.remove(target.getUUID());
        if (target.isSleeping()) target.stopSleeping();
        target.stopRiding();
        target.ejectPassengers();
        CARRYING.put(dragon.getUUID(), target.getUUID());
        CARRIED_BY.put(target.getUUID(), dragon.getUUID());
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (dragon.getTarget() == target) dragon.setTarget(null);
        if (dragon.getControllingPassenger() == null && !dragon.isFlying() && !dragon.isHovering()
                && dragon.isAllowedToTriggerFlight()) {
            dragon.setHovering(true);
        }
    }

    public static boolean release(DragonBaseEntity dragon) {
        PICKUPS.remove(dragon.getUUID());
        UUID id = CARRYING.remove(dragon.getUUID());
        if (id == null) return false;
        CARRIED_BY.remove(id);
        if (dragon.level() instanceof ServerLevel level && level.getEntity(id) instanceof LivingEntity carried) {
            carried.noPhysics = false;
            carried.fallDistance = 0;
            carried.setDeltaMovement(dragon.getDeltaMovement().scale(0.3));
            carried.setOnGround(false);
            if (!(carried instanceof Player)) FALLING.put(id, level.getGameTime() + FALL_PROTECTION_TICKS);
        }
        return true;
    }

    public static void forget(DragonBaseEntity dragon) {
        PICKUPS.remove(dragon.getUUID());
        UUID id = CARRYING.remove(dragon.getUUID());
        if (id != null) {
            CARRIED_BY.remove(id);
            FALLING.put(id, dragon.level().getGameTime() + FALL_PROTECTION_TICKS);
        }
    }

    public static void tickDragon(DragonBaseEntity dragon, ServerLevel level) {
        Pickup pickup = PICKUPS.get(dragon.getUUID());
        if (pickup != null) tickPickup(dragon, level, pickup);

        UUID id = CARRYING.get(dragon.getUUID());
        if (id == null) return;
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity carried) || !carried.isAlive() || !dragon.isAlive() || dragon.isModelDead()) {
            CARRYING.remove(dragon.getUUID());
            CARRIED_BY.remove(id);
            return;
        }

        Vec3 spot = new Vec3(dragon.getX(), dragon.getY() - carried.getBbHeight() - 0.3, dragon.getZ());
        if (!level.noCollision(carried, carried.getDimensions(carried.getPose()).makeBoundingBox(spot))) {
            spot = new Vec3(dragon.getX(), dragon.getY(), dragon.getZ());
        }
        if (carried instanceof ServerPlayer player) {
            player.connection.teleport(spot.x, spot.y, spot.z, 0F, 0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
            return;
        }
        carried.noPhysics = true;
        carried.moveTo(spot.x, spot.y, spot.z, dragon.getYRot(), carried.getXRot());
        carried.setDeltaMovement(dragon.getDeltaMovement());
        carried.fallDistance = 0;
        carried.setOnGround(false);
        carried.hurtMarked = true;
        if (carried instanceof Mob mob) mob.getNavigation().stop();
    }

    private static void tickPickup(DragonBaseEntity dragon, ServerLevel level, Pickup pickup) {
        Entity entity = level.getEntity(pickup.target());
        if (!(entity instanceof LivingEntity target) || !canCarry(dragon, target) || level.getGameTime() > pickup.expires()
                || dragon.getControllingPassenger() != null || dragon.isOrderedToSit()) {
            PICKUPS.remove(dragon.getUUID());
            return;
        }
        if (distanceToFeet(dragon, target) <= reach(dragon)) {
            grab(dragon, target);
            return;
        }
        if (!dragon.isFlying() && !dragon.isHovering()) {
            if (dragon.isAllowedToTriggerFlight()) dragon.setHovering(true);
            else if (level.getGameTime() % 10 == 0) dragon.getNavigation().moveTo(target, 1.4D);
        }
    }

    public static void tickFalling(Entity entity) {
        Long until = FALLING.get(entity.getUUID());
        if (until == null) return;
        if (entity.level().getGameTime() > until || entity.onGround() || entity.isInWater() || entity.isPassenger() || !entity.isAlive()) {
            FALLING.remove(entity.getUUID());
        } else {
            entity.fallDistance = 0;
        }
    }
}
