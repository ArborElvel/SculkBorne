package com.unddefined.sculkborne.entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 幽匿螨（Sculk Mite）。
 *
 * <p>基类是原版末影螨（{@link Endermite}），行为完全沿用末影螨：同样的 Goal 列表与索敌、
 * 同样 0.4×0.3 的体型，以及不被强制保存时经过 {@code MAX_LIFE} 刻自行消失的寿命；
 * 本类不新增任何 Goal 与状态。
 *
 * <p>作为幽匿生物实现 {@link SculkMob}，因此自动接入幽匿生物的共用约定
 * （幽匿系方块上的回血与属性加成、阳光下的虚弱与缓慢、死亡时的幽匿绽放、基础掉落、
 * 次声波压制、攻击附带幽匿侵扰等），并且不会发出振动、不会成为监守者的目标。
 *
 * <p>它不会自然生成：生成表与生成位置规则里都没有它，只在使用末影回响仪器传送后按几率出现，
 * 见 {@link com.unddefined.sculkborne.compat.enderechoing.EnderEchoingTeleportHooks}。
 *
 * <p>外观由 freshsculk 的 {@code SculkMiteCemAnimator} 计算：geo 的骨架改成 Fresh Animations 末影螨的
 * {@code body2 / head / head_s / tail / tail_s / tail1 / tail1_s / tail2} 层级后，
 * CEM 公式（待机蠕动、行走摆动与死亡蜷缩）可以逐条移植过来。
 */
public class SculkMiteEntity extends Endermite implements GeoEntity, SculkMob {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    public SculkMiteEntity(EntityType<SculkMiteEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Endermite.createAttributes();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 阳光直射下获得虚弱与缓慢
        applySunlightDebuffs();
        // 站在幽匿系方块上时按亮度反比缓慢回血
        sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);
        // 站在幽匿系方块上时临时提高移动速度与生命上限
        tickSculkBlockBonus();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit) this.triggerAnim("attack", "attack");

        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 未安装 freshsculk 时的关键帧回退；装了之后整帧姿势由 CEM 动画器托管
        controllers.add(new AnimationController<>(this, "controller", 1,
                state -> state.isMoving()
                        ? state.setAndContinue(RawAnimation.begin().thenLoop("walk"))
                        : state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
