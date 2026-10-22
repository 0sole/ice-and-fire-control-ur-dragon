package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.ai.DragonAIEscortGoal;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.mixin.MobAccessor;
import com.osole.controlurdragon.server.DragonBringer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.UUID;

@EventBusSubscriber(modid = ControlUrDragon.MODID)
public final class DragonBehaviorEvents {
    private DragonBehaviorEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        GoalSelector goals = ((MobAccessor) dragon).controlurdragon$getGoalSelector();
        goals.removeAllGoals(goal -> goal instanceof DragonAIEscortGoal || goal instanceof CudFollowGoal);
        if (Config.IMPROVED_FOLLOW.get()) goals.addGoal(4, new CudFollowGoal(dragon));
        else goals.addGoal(4, new DragonAIEscortGoal(dragon, 1.0D));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        DragonCarry.forget(dragon);
        DragonBehavior.forget(dragon);
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof DragonBaseEntity dragon) || dragon.level().isClientSide() || !dragon.isTame()) return;
        if (!DragonOrders.allowsTarget(dragon, event.getNewAboutToBeSetTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof DragonBaseEntity dragon && !dragon.level().isClientSide()) {
            DragonBehavior.onHurt(dragon, event.getSource());
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (DragonCarry.isProtected(event.getEntity()) || DragonBehavior.isFriendlyFire(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DragonBringer.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        DragonCarry.tickFalling(entity);
        DragonBehavior.tickFriendlyFire(entity);
        if (!(entity instanceof DragonBaseEntity dragon) || !dragon.isTame()) return;

        DragonCarry.tickDragon(dragon, level);
        DragonBehavior.tickRetreat(dragon, level);
        DragonBehavior.tickAttackPosition(dragon);

        if ((level.getGameTime() + dragon.getId()) % 10 != 0) return;
        if (dragon.isModelDead()) return;
        if (dragon.isVehicle()) return;
        if ((level.getGameTime() + dragon.getId()) % 20 == 0) DragonBehavior.tickAggressive(dragon, level);

        UUID targetId = DragonOrders.getAttackOrder(dragon);
        if (targetId != null) {
            Entity target = level.getEntity(targetId);
            if (target instanceof LivingEntity living && living.isAlive() && DragonOrders.canAttack(dragon, living)) {
                if (dragon.getTarget() != living) DragonOrders.setOrderedTarget(dragon, living);
                return;
            }
            DragonOrders.clearAttackOrder(dragon);
            dragon.setTarget(null);
        }

        DragonOrders.GuardArea guard = DragonOrders.getGuard(dragon);
        if (guard == null) return;
        if (dragon.getCommand() != 0) {
            DragonOrders.clearGuard(dragon);
            return;
        }
        if (!guard.dimension().equals(level.dimension().location().toString())) return;
        BlockPos center = guard.center();
        int radius = guard.radius();

        LivingEntity current = dragon.getTarget();
        if (current != null && (!current.isAlive() || current.distanceToSqr(center.getCenter()) > radius * radius * 2.25)) {
            dragon.setTarget(null);
            current = null;
        }
        if (current != null) return;

        AABB box = new AABB(center).inflate(radius);
        LivingEntity enemy = level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e instanceof Enemy && e.isAlive() && DragonOrders.canAttack(dragon, e)
                                && e.distanceToSqr(center.getCenter()) <= radius * radius)
                .stream().min(Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
        if (enemy != null) {
            DragonOrders.setOrderedTarget(dragon, enemy);
            return;
        }
        if (!dragon.isFlying() && !dragon.isHovering() && dragon.distanceToSqr(center.getCenter()) > radius * radius * 0.5) {
            dragon.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.2D);
        }
    }
}
