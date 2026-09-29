package com.unddefined.sculkborne.client.model.cem;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;

/**
 * sculkborne 暴露给 CEM 附属 mod 的客户端接口：一个生物每帧的姿势计算入口。
 *
 * <p>这里刻意只用原版与 GeckoLib 的类型（{@link LivingEntity} 与 {@link AnimationState}），
 * 不引用附属 mod 的任何类，因此 sculkborne 可以脱离 CEM 附属独立编译、独立运行；
 * 附属 mod 只要把自己的动画器注册进 {@link CemAnimatorRegistry} 即可接管姿势。
 *
 * @param <T> 该动画器负责的生物类型
 */
@FunctionalInterface
public interface CemAnimatorHandle<T extends LivingEntity> {

    /**
     * 把本帧的姿势写到骨骼上，由 {@code GeoModel#setCustomAnimations} 调用。
     *
     * @param entity         正在渲染的生物
     * @param animationState GeckoLib 传入的动画状态
     */
    void apply(T entity, AnimationState<?> animationState);
}
