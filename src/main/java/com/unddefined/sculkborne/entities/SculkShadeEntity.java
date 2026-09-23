package com.unddefined.sculkborne.entities;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 幽影（Sculk Shade）。
 *
 * <p>基类是原版恼鬼（{@link Vex}），保留恼鬼的飞行与战斗行为：无重力飞行、冲向目标的自杀式冲锋、
 * 以自身为中心的随机飘移；召唤者相关的那部分行为没有召唤者时不生效，因此自然生成的个体只按普通怪物索敌。
 * 与恼鬼唯一的差别是空手——生成时不给主手发铁剑，见
 * {@link #populateDefaultEquipmentSlots(RandomSource, DifficultyInstance)}。
 *
 * <p>作为幽匿生物实现 {@link SculkMob}，因此自动接入幽匿生物的共用约定
 * （幽匿系方块上的回血与属性加成、阳光下的虚弱与缓慢、死亡时的幽匿绽放、基础掉落、
 * 次声波压制、攻击附带幽匿侵扰等），并且不会成为监守者的目标。
 */
public class SculkShadeEntity extends Vex implements GeoEntity, SculkMob {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    public SculkShadeEntity(EntityType<SculkShadeEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes();
    }

    /**
     * 生成时不发装备，即不像原版恼鬼那样默认握持铁剑。
     *
     * <p>刻意不调用 {@code super}：原版恼鬼在这里给自己塞一把铁剑（掉落概率 0），
     * 那把剑不但模型上没有对应骨骼显示不出来，还会按物品属性修正悄悄提高攻击力。
     * 幽影是空手的，攻击力只取 {@link Vex#createAttributes()} 里的 {@code ATTACK_DAMAGE}。
     */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
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
        // 模型目前只有绑定姿势，没有关键帧动画，因此不注册动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
