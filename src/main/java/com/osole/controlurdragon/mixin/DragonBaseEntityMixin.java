package com.osole.controlurdragon.mixin;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice and Fire overrides kill() with remove(KILLED): a living dragon disappears without dying,
 * without a corpse and without a death message. Any mod that calls kill() (Iron's Spells' Heartstop,
 * Relics, /kill, ...) therefore deletes dragons silently. This makes kill() a normal death again.
 */
@Mixin(value = DragonBaseEntity.class, remap = false)
public abstract class DragonBaseEntityMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void controlurdragon$dieInsteadOfVanishing(CallbackInfo ci) {
        DragonBaseEntity self = (DragonBaseEntity) (Object) this;
        // Corpses keep Ice and Fire's behaviour (kill() removes the corpse).
        if (!Config.FIX_SILENT_KILL.get() || self.isModelDead() || self.level().isClientSide()) return;
        ci.cancel();
        // Same as vanilla LivingEntity.kill().
        self.hurt(self.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
