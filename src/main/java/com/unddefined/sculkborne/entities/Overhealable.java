package com.unddefined.sculkborne.entities;

/**
 * 允许把生命值设到血量上限之上。
 *
 * <p>原版 {@code LivingEntity#setHealth}（以及 {@code heal}）都会把生命值夹在 0 ~ 血量上限之间，
 * 想要超过上限只能直接写生命值的同步数据，见
 * {@code com.unddefined.sculkborne.mixin.LivingEntityMixin}。
 *
 * <p>该接口由混入实现在所有 {@code LivingEntity} 上，使用时把实体转过来即可：
 * {@code ((Overhealable) entity).sculkborne$setHealthAboveMax(health)}。
 */
public interface Overhealable {

    /**
     * 把生命值设成 {@code health}，不做上限夹取。
     *
     * <p>超过上限的部分会一直保留到受伤、或者血量上限发生变化（原版在血量上限属性变动时会把生命值夹回，
     * 例如幽匿系方块的速度与生命上限加成失效）为止。
     *
     * @param health 新的生命值，不应小于 0
     */
    void sculkborne$setHealthAboveMax(float health);
}
