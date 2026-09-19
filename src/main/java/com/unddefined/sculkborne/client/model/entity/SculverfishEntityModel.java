package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.SculverfishCemAnimator;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class SculverfishEntityModel<T extends SculverfishEntity> extends DefaultedEntityGeoModel<T> {
    private final SculverfishCemAnimator cemAnimator = new SculverfishCemAnimator(this);

    public SculverfishEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculverfish"));
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
