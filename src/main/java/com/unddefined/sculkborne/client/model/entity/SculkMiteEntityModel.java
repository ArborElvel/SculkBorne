package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.SculkMiteCemAnimator;
import com.unddefined.sculkborne.entities.SculkMiteEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class SculkMiteEntityModel<T extends SculkMiteEntity> extends DefaultedEntityGeoModel<T> {
    private final SculkMiteCemAnimator cemAnimator = new SculkMiteCemAnimator(this);

    public SculkMiteEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_mite"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        // 姿势完全由 cemAnimator 计算（CEM 公式给出的是绝对姿势），这里不再走 GeckoLib 的默认头部朝向
        this.cemAnimator.apply(animatable, animationState);
    }
}
