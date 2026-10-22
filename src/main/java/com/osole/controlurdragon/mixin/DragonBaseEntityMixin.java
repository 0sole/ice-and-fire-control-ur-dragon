package com.osole.controlurdragon.mixin;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DragonBaseEntity.class, remap = false)
public abstract class DragonBaseEntityMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void controlurdragon$dieInsteadOfVanishing(CallbackInfo ci) {
        DragonBaseEntity self = (DragonBaseEntity) (Object) this;

        if (!Config.FIX_SILENT_KILL.get() || self.isModelDead() || self.level().isClientSide()) return;
        ci.cancel();

        self.hurt(self.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
