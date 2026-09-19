package com.unddefined.sculkborne.mixin;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/client/renderer/FogRenderer$FogData")
public interface FogDataAccessor {
    @Accessor("start")
    void setStart(float value);

    @Accessor("end")
    void setEnd(float value);

    @Accessor("mode")
    FogRenderer.FogMode getMode();
}
