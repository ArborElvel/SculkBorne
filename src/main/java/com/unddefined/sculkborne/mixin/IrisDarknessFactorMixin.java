package com.unddefined.sculkborne.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public class IrisDarknessFactorMixin {
    @Unique
    private static final float SCULK_VEIL_DARKNESS_FACTOR = 1.3F;

    @Inject(method = "getDarknessFactor", at = @At("RETURN"), cancellable = true, remap = false)
    private static void sculkborne$modifyDarknessFactor(CallbackInfoReturnable<Float> cir) {
        MobEffectInstance darkness = sculkborne$getCameraDarkness();

        if (darkness != null && darkness.is(MobEffects.DARKNESS) && darkness.getAmplifier() == 1)
            cir.setReturnValue(Mth.clamp(cir.getReturnValue() * SCULK_VEIL_DARKNESS_FACTOR, 0.0F, 1.3F));
    }

    @Unique
    private static MobEffectInstance sculkborne$getCameraDarkness() {
        var cameraEntity = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
        if (cameraEntity instanceof net.minecraft.world.entity.LivingEntity livingEntity)
            return livingEntity.getEffect(MobEffects.DARKNESS);
        return null;
    }
}
