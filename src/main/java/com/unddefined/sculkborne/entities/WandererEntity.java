package com.unddefined.sculkborne.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 徘徊者（Wanderer），幽匿末影人。
 *
 * <p>基类是原版末影人（{@link EnderMan}），行为完全沿用末影人：同样的 Goal 列表、索敌与
 * 被注视后暴怒、同样的传送、搬起与放下方块，以及 0.6×2.9 的体型；
 * 本类不新增任何 Goal、状态或技能。
 *
 * <p>作为幽匿生物实现 {@link SculkMob}，因此自动接入幽匿生物的共用约定
 * （幽匿系方块上的回血与属性加成、阳光下的虚弱与缓慢、死亡时的幽匿绽放、基础掉落、
 * 次声波压制、攻击附带幽匿侵扰等），并且不会发出振动、不会成为监守者的目标。
 *
 * <p>骨架与贴图见 {@code assets/sculkborne/geo/entity/wanderer.geo.json} 与
 * {@code assets/sculkborne/textures/entity/wanderer.png}；脸上是异瞳，右眼沿用原版末影人的瞳色、
 * 左眼是 {@code #29DFEB}，由客户端的 {@code WandererEyesLayer} 用自发光贴图覆盖出来；
 * 待机、行走、注视与搬运方块的姿势则是 Fresh Animations 末影人 CEM 动画的移植，
 * 见客户端的 {@code WandererCemAnimator}；
 * 触发振动与干扰传送等专属行为不在这里实现。
 */
public class WandererEntity extends EnderMan implements GeoEntity, SculkMob {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    public WandererEntity(EntityType<WandererEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return EnderMan.createAttributes();
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 姿势完全由客户端的 WandererCemAnimator 计算（CEM 公式本身包含待机/行走/注视/受伤/搬运），
        // 因此不注册关键帧动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
