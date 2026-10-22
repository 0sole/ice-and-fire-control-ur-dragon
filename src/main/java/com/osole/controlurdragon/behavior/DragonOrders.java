package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orders given from the menu or hotkeys that Ice and Fire has no command for: attack a chosen target,
 * guard an area. The guard area is saved on the dragon itself, the attack order only lasts until the target is gone.
 */
public final class DragonOrders {
    private static final String GUARD_TAG = "controlurdragon_guard";

    /** dragon UUID → ordered target UUID */
    private static final Map<UUID, UUID> ATTACK_ORDERS = new ConcurrentHashMap<>();

    private DragonOrders() {
    }

    public record GuardArea(String dimension, BlockPos center, int radius) {
    }

    // ---- attack ---------------------------------------------------------------------------------

    /** @return false if the target is not allowed (the owner, the owner's own pets, ...). */
    public static boolean canAttack(DragonBaseEntity dragon, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target == dragon) return false;
        if (dragon.getOwner() != null && target == dragon.getOwner()) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        // Never turn on the owner's own pets and dragons.
        return !(target instanceof OwnableEntity ownable && dragon.getOwnerUUID() != null
                && dragon.getOwnerUUID().equals(ownable.getOwnerUUID()));
    }

    public static void attack(DragonBaseEntity dragon, LivingEntity target) {
        if (dragon.isOrderedToSit() || dragon.getCommand() == 1) dragon.setCommand(2);
        dragon.setOrderedToSit(false);
        dragon.setInSittingPose(false);
        dragon.setTarget(target);
        ATTACK_ORDERS.put(dragon.getUUID(), target.getUUID());
    }

    public static UUID getAttackOrder(DragonBaseEntity dragon) {
        return ATTACK_ORDERS.get(dragon.getUUID());
    }

    public static void clearAttackOrder(DragonBaseEntity dragon) {
        ATTACK_ORDERS.remove(dragon.getUUID());
    }

    // ---- guard ----------------------------------------------------------------------------------

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

    /** A plain command (sit, follow, wander, come) cancels attack and guard orders. */
    public static void clearAll(DragonBaseEntity dragon) {
        clearAttackOrder(dragon);
        clearGuard(dragon);
        if (dragon.getTarget() != null) dragon.setTarget(null);
    }
}
