package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.SculkZombieCemAnimator;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public class SculkZombieEntityModel<T extends SculkZombieEntity> extends DefaultedEntityGeoModel<T> {
    private final SculkZombieCemAnimator cemAnimator = new SculkZombieCemAnimator(this);

    public SculkZombieEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_zombie"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {return RenderType.entityTranslucent(texture);}

    @Override
    public ResourceLocation getTextureResource(T animatable, @Nullable GeoRenderer<T> renderer) {
        return buildFormattedTexturePath(ResourceLocation.fromNamespaceAndPath("sculkborne",
                "sculk_zombie" + (animatable.isVibrationActive() ? "_active" : "")));
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        // 姿势完全由 cemAnimator 计算（CEM 公式给出的是绝对姿势），这里不再走 GeckoLib 的默认头部朝向
        this.cemAnimator.apply(animatable, animationState);
    }
}
