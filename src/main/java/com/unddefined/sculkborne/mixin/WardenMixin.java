package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.entities.SculkMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
public class WardenMixin {
    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void cannotTargetSculkMob(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof SculkMob) cir.setReturnValue(false);
    }
}
