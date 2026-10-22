package com.osole.controlurdragon.mixin;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.util.dragon.IafDragonFlightManager;
import com.osole.controlurdragon.behavior.DragonFollowLogic;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While following or guarding, a flying dragon heads for a steady point instead of Ice and Fire's
 * random "block in view near the owner", which made it fly in zigzags.
 */
@Mixin(value = IafDragonFlightManager.class, remap = false)
public abstract class DragonFlightManagerMixin {
    @Shadow
    @Final
    private DragonBaseEntity dragon;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void controlurdragon$steadyFlight(CallbackInfo ci) {
        Vec3 target = DragonFollowLogic.flightTarget(this.dragon);
        if (target == null) return;
        ((IafDragonFlightManager) (Object) this).setFlightTarget(target);
        if (target.y >= this.dragon.getY())
            this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().add(0, 0.1D, 0));
        ci.cancel();
    }
}
