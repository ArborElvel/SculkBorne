package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.entities.SculkMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_VEIL;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
    private void dampensVibrations(CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof SculkMob) cir.setReturnValue(true);

        if ((Object) this instanceof Player player) if (player.hasEffect(SCULK_VEIL)) cir.setReturnValue(true);
    }
}
