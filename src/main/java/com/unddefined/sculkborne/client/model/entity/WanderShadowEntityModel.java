package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.WanderShadowEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 徘徊者残影的模型：骨架与贴图是 {@code wander_shadow} 那一份，
 * 姿势由 {@code EndermanVanillaAnimator} 给出的徘徊者动作接管。
 */
public class WanderShadowEntityModel<T extends WanderShadowEntity> extends CemEntityModel<T> {

    public WanderShadowEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "wander_shadow"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        // 残影是半透明的，和徘徊者用同一个半透明渲染类型
        return RenderType.entityTranslucent(texture);
    }
}
