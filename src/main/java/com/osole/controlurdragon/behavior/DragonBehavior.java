package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.item.component.DragonHornComponent;
import com.iafenvoy.iceandfire.registry.IafDataComponents;
import com.iafenvoy.iceandfire.registry.IafItems;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.tracking.DragonTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonBehavior {
    private static final int RETREAT_TICKS = 400;
    private static final float RECOVERED_HEALTH = 0.5F;

    private record Retreat(DragonSettings.LowHealth mode, UUID from, long until) {
    }

    private static final Map<UUID, Retreat> RETREATS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FRIENDLY_FIRE = new ConcurrentHashMap<>();

    private DragonBehavior() {
    }

    public static boolean isRetreating(DragonBaseEntity dragon) {
        return RETREATS.containsKey(dragon.getUUID());
    }

    public static void forget(DragonBaseEntity dragon) {
        RETREATS.remove(dragon.getUUID());
    }

    public static void onHurt(DragonBaseEntity dragon, DamageSource source) {
        if (!dragon.isTame() || dragon.isModelDead() || dragon.getControllingPassenger() != null) return;
        LivingEntity owner = dragon.getOwner();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != owner ? living : null;

        if (attacker != null && dragon.isOrderedToSit() && DragonSettings.wakesOnHit(dragon)) {
            boolean ownerNearby = owner != null && owner.level() == dragon.level() && owner.distanceToSqr(dragon) < 64 * 64;
            dragon.setCommand(ownerNearby ? 2 : 0);
            dragon.setInSittingPose(false);
            if (!isRetreating(dragon)) dragon.setTarget(attacker);
        }

        if (isRetreating(dragon) || dragon.getHealth() > dragon.getMaxHealth() * Config.LOW_HEALTH_PERCENT.get() / 100F) return;
        DragonSettings.LowHealth mode = DragonSettings.lowHealth(dragon);
        switch (mode) {
            case FIGHT -> {
            }
            case HORN -> {
                if (!storeInHorn(dragon)) startRetreat(dragon, DragonSettings.LowHealth.COME, attacker);
            }
            default -> startRetreat(dragon, mode, attacker);
        }
    }

    private static void startRetreat(DragonBaseEntity dragon, DragonSettings.LowHealth mode, LivingEntity attacker) {
        DragonOrders.clearAttackOrder(dragon);
        dragon.setTarget(null);
        if (mode == DragonSettings.LowHealth.COME) {
            DragonOrders.clearGuard(dragon);
            dragon.setCommand(2);
        }
        RETREATS.put(dragon.getUUID(), new Retreat(mode, attacker == null ? null : attacker.getUUID(),
                dragon.level().getGameTime() + RETREAT_TICKS));
        if (dragon.getOwner() instanceof Player owner) {
            owner.displayClientMessage(Component.translatable("controlurdragon.low_health." + mode.name().toLowerCase(),
                    dragon.getName()).withStyle(ChatFormatting.GOLD), true);
        }
        if (!dragon.isFlying() && !dragon.isHovering() && dragon.isAllowedToTriggerFlight()) dragon.setHovering(true);
    }

    private static boolean storeInHorn(DragonBaseEntity dragon) {
        if (!(dragon.getOwner() instanceof ServerPlayer owner)) return false;
        Inventory inventory = owner.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(IafItems.DRAGON_HORN.get()) || stack.has(IafDataComponents.DRAGON_HORN.get())) continue;
            DragonCarry.release(dragon);
            DragonOrders.clearAll(dragon);
            dragon.ejectPassengers();
            CompoundTag data = new CompoundTag();
            dragon.save(data);
            stack.set(IafDataComponents.DRAGON_HORN.get(), new DragonHornComponent(
                    BuiltInRegistries.ENTITY_TYPE.getKey(dragon.getType()), dragon.getUUID(), data));
            DragonTracker.markStored(dragon.getUUID());
            dragon.remove(Entity.RemovalReason.DISCARDED);
            owner.level().playSound(null, owner.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 1.0F, 0.75F);
            owner.displayClientMessage(Component.translatable("controlurdragon.low_health.horn", dragon.getName())
                    .withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        return false;
    }

    public static void tickRetreat(DragonBaseEntity dragon, ServerLevel level) {
        Retreat retreat = RETREATS.get(dragon.getUUID());
        if (retreat == null) return;
        if (level.getGameTime() > retreat.until() || dragon.getHealth() >= dragon.getMaxHealth() * RECOVERED_HEALTH
                || dragon.getControllingPassenger() != null || dragon.isOrderedToSit()) {
            RETREATS.remove(dragon.getUUID());
            return;
        }
        if (dragon.getTarget() != null && DragonOrders.getAttackOrder(dragon) == null) dragon.setTarget(null);
        if (retreat.mode() == DragonSettings.LowHealth.FLEE && !dragon.isFlying() && !dragon.isHovering()
                && level.getGameTime() % 10 == 0) {
            Vec3 away = fleePoint(dragon, level, retreat);
            dragon.getNavigation().moveTo(away.x, away.y, away.z, 1.6D);
        }
    }

    private static Vec3 fleePoint(DragonBaseEntity dragon, ServerLevel level, Retreat retreat) {
        Entity from = retreat.from() == null ? null : level.getEntity(retreat.from());
        Vec3 direction;
        if (from != null) {
            direction = new Vec3(dragon.getX() - from.getX(), 0, dragon.getZ() - from.getZ());
        } else {
            float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
            direction = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        }
        if (direction.lengthSqr() < 1.0E-4) direction = new Vec3(1, 0, 0);
        return dragon.position().add(direction.normalize().scale(32));
    }

    public static Vec3 retreatFlightTarget(DragonBaseEntity dragon) {
        Retreat retreat = RETREATS.get(dragon.getUUID());
        if (retreat == null || retreat.mode() != DragonSettings.LowHealth.FLEE || !(dragon.level() instanceof ServerLevel level)) return null;
        Vec3 away = fleePoint(dragon, level, retreat);
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(away.x), Mth.floor(away.z));
        return new Vec3(away.x, ground + 16 + dragon.getDragonStage() * 2, away.z);
    }

    public static void tickAggressive(DragonBaseEntity dragon, ServerLevel level) {
        if (DragonOrders.getStance(dragon) != DragonOrders.Stance.AGGRESSIVE) return;
        if (dragon.getTarget() != null || dragon.isOrderedToSit() || dragon.isVehicle() || dragon.isSleeping()
                || isRetreating(dragon) || dragon.getDragonStage() < 2) return;
        double range = 20 + dragon.getDragonStage() * 4;
        LivingEntity prey = level.getEntitiesOfClass(LivingEntity.class, dragon.getBoundingBox().inflate(range),
                        e -> isFairGame(dragon, e, level) && e.distanceToSqr(dragon) <= range * range)
                .stream().filter(dragon::hasLineOfSight)
                .min(Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
        if (prey != null) dragon.setTarget(prey);
    }

    private static boolean isFairGame(DragonBaseEntity dragon, LivingEntity entity, ServerLevel level) {
        if (entity instanceof ArmorStand || !DragonOrders.canAttack(dragon, entity)) return false;
        if (entity instanceof Player && !level.getServer().isPvpAllowed()) return false;
        return !(entity instanceof DragonBaseEntity other && other.isTame() && other.getOwnerUUID() != null
                && other.getOwnerUUID().equals(dragon.getOwnerUUID()));
    }

    public static void tickAttackPosition(DragonBaseEntity dragon) {
        if (dragon.getTarget() == null || dragon.getControllingPassenger() != null || dragon.isOrderedToSit() || isRetreating(dragon)) return;
        if (!DragonSettings.matchesAttackStyle(dragon)) DragonSettings.applyAttackStyle(dragon);
        switch (DragonSettings.attackPosition(dragon)) {
            case GROUND -> {
                if ((dragon.isFlying() || dragon.isHovering()) && !dragon.isInWater()) {
                    dragon.setFlying(false);
                    dragon.setHovering(false);
                    dragon.setTackling(false);
                }
            }
            case AIR -> {
                if (!dragon.isFlying() && !dragon.isHovering() && dragon.isAllowedToTriggerFlight()) dragon.setHovering(true);
                LivingEntity target = dragon.getTarget();
                double range = 14 + dragon.getDragonStage() * 3;
                if (DragonSettings.rangedFromAir(dragon) && (dragon.isFlying() || dragon.isHovering())
                        && dragon.distanceTo(target) <= range + 16 && dragon.hasLineOfSight(target)) {
                    dragon.breathAttack(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), true);
                }
            }
            default -> {
            }
        }
    }

    public static boolean isFriendlyFire(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof DragonBaseEntity attacker) || attacker == victim || attacker.getOwnerUUID() == null) return false;
        UUID victimOwner = victim instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
        if (!attacker.getOwnerUUID().equals(victimOwner)) return false;
        FRIENDLY_FIRE.put(victim.getUUID(), victim.level().getGameTime());
        return true;
    }

    public static void tickFriendlyFire(Entity entity) {
        Long time = FRIENDLY_FIRE.get(entity.getUUID());
        if (time == null) return;
        if (entity.level().getGameTime() - time <= 1) entity.clearFire();
        else FRIENDLY_FIRE.remove(entity.getUUID());
    }

    public static Vec3 standoffPoint(DragonBaseEntity dragon, LivingEntity target) {
        if (!DragonSettings.rangedFromAir(dragon)) return null;
        double range = 14 + dragon.getDragonStage() * 3;
        double angle = Math.atan2(dragon.getZ() - target.getZ(), dragon.getX() - target.getX()) + 0.2;
        double height = target.getY() + target.getBbHeight() + 6 + dragon.getDragonStage() * 2;
        return new Vec3(target.getX() + Math.cos(angle) * range, height, target.getZ() + Math.sin(angle) * range);
    }
}
