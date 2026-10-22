package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonOrders {
    private static final String GUARD_TAG = "controlurdragon_guard";
    private static final String STANCE_TAG = "controlurdragon_stance";

    private static final Map<UUID, UUID> ATTACK_ORDERS = new ConcurrentHashMap<>();
    private static boolean ordering;

    private DragonOrders() {
    }

    public enum Stance {
        NORMAL, DEFENSIVE, PASSIVE, AGGRESSIVE;

        public static Stance byId(int id) {
            Stance[] values = values();
            return id >= 0 && id < values.length ? values[id] : NORMAL;
        }
    }

    public record GuardArea(String dimension, BlockPos center, int radius) {
    }

    public static boolean canAttack(DragonBaseEntity dragon, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target == dragon) return false;
        if (dragon.getOwner() != null && target == dragon.getOwner()) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (DragonCarry.isCarried(target)) return false;
        return !(target instanceof OwnableEntity ownable && dragon.getOwnerUUID() != null
                && dragon.getOwnerUUID().equals(ownable.getOwnerUUID()));
    }

    public static void setOrderedTarget(DragonBaseEntity dragon, LivingEntity target) {
        ordering = true;
        try {
            dragon.setTarget(target);
        } finally {
            ordering = false;
        }
    }

    public static boolean allowsTarget(DragonBaseEntity dragon, LivingEntity target) {
        if (target == null || ordering) return true;
        if (DragonCarry.isCarried(target) || DragonBehavior.isRetreating(dragon)) return false;
        return switch (getStance(dragon)) {
            case NORMAL, AGGRESSIVE -> true;
            case PASSIVE -> false;
            case DEFENSIVE -> isAggressor(dragon, target);
        };
    }

    private static boolean isAggressor(DragonBaseEntity dragon, LivingEntity target) {
        LivingEntity owner = dragon.getOwner();
        if (target == dragon.getLastHurtByMob()) return true;
        if (owner != null && target == owner.getLastHurtByMob()) return true;
        return target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == dragon || mob.getTarget() == owner);
    }

    public static Stance getStance(DragonBaseEntity dragon) {
        return Stance.byId(dragon.getPersistentData().getInt(STANCE_TAG));
    }

    public static void setStance(DragonBaseEntity dragon, Stance stance) {
        dragon.getPersistentData().putInt(STANCE_TAG, stance.ordinal());
        LivingEntity target = dragon.getTarget();
        if (target != null && getAttackOrder(dragon) == null && !allowsTarget(dragon, target)) dragon.setTarget(null);
    }

    public static void attack(DragonBaseEntity dragon, LivingEntity target) {
        if (dragon.isOrderedToSit() || dragon.getCommand() == 1) dragon.setCommand(2);
        dragon.setOrderedToSit(false);
        dragon.setInSittingPose(false);
        ATTACK_ORDERS.put(dragon.getUUID(), target.getUUID());
        setOrderedTarget(dragon, target);
    }

    public static UUID getAttackOrder(DragonBaseEntity dragon) {
        return ATTACK_ORDERS.get(dragon.getUUID());
    }

    public static void clearAttackOrder(DragonBaseEntity dragon) {
        ATTACK_ORDERS.remove(dragon.getUUID());
    }

    public static void stop(DragonBaseEntity dragon) {
        clearAttackOrder(dragon);
        clearGuard(dragon);
        dragon.setLastHurtByMob(null);
        if (dragon.getTarget() != null) dragon.setTarget(null);
    }

    public static void guard(DragonBaseEntity dragon, BlockPos center, int radius) {
        dragon.setCommand(0);
        dragon.setOrderedToSit(false);
        dragon.setInSittingPose(false);
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dragon.level().dimension().location().toString());
        tag.putInt("X", center.getX());
        tag.putInt("Y", center.getY());
        tag.putInt("Z", center.getZ());
        tag.putInt("R", radius);
        dragon.getPersistentData().put(GUARD_TAG, tag);
    }

    public static GuardArea getGuard(DragonBaseEntity dragon) {
        CompoundTag data = dragon.getPersistentData();
        if (!data.contains(GUARD_TAG)) return null;
        CompoundTag tag = data.getCompound(GUARD_TAG);
        return new GuardArea(tag.getString("Dim"), new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")), tag.getInt("R"));
    }

    public static void clearGuard(DragonBaseEntity dragon) {
        dragon.getPersistentData().remove(GUARD_TAG);
    }

    public static void clearAll(DragonBaseEntity dragon) {
        clearAttackOrder(dragon);
        clearGuard(dragon);
        if (dragon.getTarget() != null) dragon.setTarget(null);
    }
}
