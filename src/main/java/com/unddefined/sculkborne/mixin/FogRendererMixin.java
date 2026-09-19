package com.unddefined.sculkborne.mixin;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/renderer/FogRenderer$DarknessFogFunction")
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void setupFog(@Coerce Object fogData, LivingEntity entity, MobEffectInstance effectInstance,
                          float farPlaneDistance, float partialTick, CallbackInfo ci) {
        float endValue = effectInstance.getAmplifier() == 0 ? 15F : 7.5F;
        float f = Mth.lerp(effectInstance.getBlendFactor(entity, partialTick), farPlaneDistance, endValue);
        FogDataAccessor accessor = (FogDataAccessor) fogData;
        accessor.setStart(accessor.getMode() == FogRenderer.FogMode.FOG_SKY ? 0.0F : f * 0.75F);
        accessor.setEnd(f);

        ci.cancel();
    }

}