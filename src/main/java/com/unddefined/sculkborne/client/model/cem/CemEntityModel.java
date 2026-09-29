package com.unddefined.sculkborne.client.model.cem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 支持 CEM 姿势接管的实体模型基类。
 *
 * <p>姿势优先级：注册表里存在该生物类型的 CEM 动画器时，整帧姿势由动画器托管（CEM 公式给出的是
 * 绝对姿势，不能再叠加关键帧动画）；没有注册时回退到 {@link DefaultedEntityGeoModel} 的默认行为，
 * 即播放 {@code registerControllers} 里注册的关键帧动画。
 *
 * @param <T> 实体类型
 */
public class CemEntityModel<T extends LivingEntity & GeoAnimatable> extends DefaultedEntityGeoModel<T> {

    public CemEntityModel(ResourceLocation assetSubpath) {
        super(assetSubpath);
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        if (!CemAnimatorRegistry.apply(this, animatable, animationState)) {
            super.setCustomAnimations(animatable, instanceId, animationState);
        }
    }
}
