package com.osole.controlurdragon.mixin;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.util.dragon.DragonUtils;
import com.osole.controlurdragon.behavior.DragonSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DragonUtils.class, remap = false)
public abstract class DragonUtilsMixin {
    @Inject(method = "canGrief(Lcom/iafenvoy/iceandfire/entity/DragonBaseEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void controlurdragon$griefToggle(DragonBaseEntity dragon, CallbackInfoReturnable<Boolean> cir) {
        if (dragon.isTame() && !DragonSettings.breaksBlocks(dragon)) cir.setReturnValue(false);
    }

    @Inject(method = "canSoftGrief", at = @At("HEAD"), cancellable = true)
    private static void controlurdragon$softGriefToggle(DragonBaseEntity dragon, CallbackInfoReturnable<Boolean> cir) {
        if (dragon.isTame() && !DragonSettings.breaksBlocks(dragon)) cir.setReturnValue(false);
    }
}
