package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Replaces Ice and Fire's ground follow (DragonAIEscortGoal).
 * - stops at a comfortable distance instead of walking into the owner
 * - runs when far behind
 * - re-plans the route twice a second instead of every few blocks the owner moves
 * - takes off when the owner is far away, high up or flying
 */
public class CudFollowGoal extends Goal {
    private final DragonBaseEntity dragon;
    private int repathTicks;

    public CudFollowGoal(DragonBaseEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity owner() {
        LivingEntity owner = this.dragon.getOwner();
        return owner != null && owner.isAlive() && owner.level() == this.dragon.level() ? owner : null;
    }

    private boolean basicConditions() {
        return Config.IMPROVED_FOLLOW.get() && this.dragon.getCommand() == 2 && this.dragon.getTarget() == null
                && this.dragon.canMove() && !this.dragon.isFlying() && !this.dragon.isHovering() && this.owner() != null;
    }

    @Override
    public boolean canUse() {
        return this.basicConditions()
                && DragonFollowLogic.horizontalDistance(this.dragon, this.owner()) > DragonFollowLogic.followDistance(this.dragon);
    }

    @Override
    public boolean canContinueToUse() {
        return this.basicConditions()
                && DragonFollowLogic.horizontalDistance(this.dragon, this.owner()) > DragonFollowLogic.followDistance(this.dragon) * 0.75F;
    }

    @Override
    public void start() {
        this.repathTicks = 0;
    }

    @Override
    public void stop() {
        this.dragon.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity owner = this.owner();
        if (owner == null) return;
        this.dragon.getLookControl().setLookAt(owner, 10.0F, this.dragon.getMaxHeadXRot());

        float keep = DragonFollowLogic.followDistance(this.dragon);
        float distance = this.dragon.distanceTo(owner);
        boolean shouldFly = distance > Math.max(32F, keep * 3F) || owner.getY() - this.dragon.getY() > 8 || owner.isFallFlying();
        if (shouldFly && this.dragon.isAllowedToTriggerFlight()) {
            this.dragon.getNavigation().stop();
            this.dragon.setHovering(true);
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = 10;
            double speed = distance > keep * 2.5F ? 1.6D : 1.2D;
            Vec3 point = DragonFollowLogic.pointNearOwner(this.dragon, owner, keep * 0.7F);
            if (!this.dragon.getNavigation().moveTo(point.x, point.y, point.z, speed))
                this.dragon.getNavigation().moveTo(owner, speed);
        }
    }
}
