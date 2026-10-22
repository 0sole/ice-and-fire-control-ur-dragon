package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.ai.DragonAIEscortGoal;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.mixin.MobAccessor;
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
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Comparator;
import java.util.UUID;

/**
 * Installs the better follow goal and keeps attack / guard orders going.
 */
@EventBusSubscriber(modid = ControlUrDragon.MODID)
public final class DragonBehaviorEvents {
    private DragonBehaviorEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        GoalSelector goals = ((MobAccessor) dragon).controlurdragon$getGoalSelector();
        goals.removeAllGoals(goal -> goal instanceof DragonAIEscortGoal || goal instanceof CudFollowGoal);
        // Ice and Fire's escort goal has priority 4 as well. When the config option is off our goal simply never runs,
        // so put the old goal back in that case.
        if (Config.IMPROVED_FOLLOW.get()) goals.addGoal(4, new CudFollowGoal(dragon));
        else goals.addGoal(4, new DragonAIEscortGoal(dragon, 1.0D));
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBaseEntity dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + dragon.getId()) % 10 != 0) return;
        if (!dragon.isTame() || dragon.isModelDead() || dragon.isVehicle()) return;

        // ---- ordered attack ----
        UUID targetId = DragonOrders.getAttackOrder(dragon);
        if (targetId != null) {
            Entity target = level.getEntity(targetId);
            if (target instanceof LivingEntity living && living.isAlive() && DragonOrders.canAttack(dragon, living)) {
                if (dragon.getTarget() != living) dragon.setTarget(living);
                return;
            }
            DragonOrders.clearAttackOrder(dragon);
            dragon.setTarget(null);
        }

        // ---- guard area ----
        DragonOrders.GuardArea guard = DragonOrders.getGuard(dragon);
        if (guard == null) return;
        if (dragon.getCommand() != 0) {
            // Owner gave another command some other way (e.g. right-click with the staff): stop guarding.
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
        if (current == null) {
            AABB box = new AABB(center).inflate(radius);
            LivingEntity enemy = level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> e instanceof Enemy && e.isAlive() && DragonOrders.canAttack(dragon, e)
                                    && e.distanceToSqr(center.getCenter()) <= radius * radius)
                    .stream().min(Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
            if (enemy != null) {
                dragon.setTarget(enemy);
                return;
            }
            // Nothing to fight: walk back if it wandered too far. Flying dragons are steered by the flight mixin.
            if (!dragon.isFlying() && !dragon.isHovering() && dragon.distanceToSqr(center.getCenter()) > radius * radius * 0.5) {
                dragon.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.2D);
            }
        }
    }
}
