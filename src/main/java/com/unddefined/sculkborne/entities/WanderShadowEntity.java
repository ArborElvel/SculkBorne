package com.unddefined.sculkborne.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 徘徊者残影（Wanderer Shadow）：徘徊者瞬移后留在原地的一道影子。
 *
 * <p>只在 {@link WandererEntity} 瞬移之后有几率留下，原地定型 {@value #LIFETIME_TICKS} 刻后自行消失，
 * 见 {@link WandererEntity#randomTeleport(double, double, double, boolean)}。传送前什么目标、
 * 什么朝向，它就照抄什么，之后不再自己索敌——目标打死了或跑掉了，它就原地等着消失。
 *
 * <p>它只继承 {@link Monster}（外加渲染用的 {@link GeoEntity}），刻意不是幽匿生物：
 * 没有幽匿生物的共用规则（不掉幽匿生物的基础掉落、不参与幽匿生物的自然生成、
 * 也没有阳光下的虚弱与缓慢等），也不会掠夺经验、不会瞬移、不会出现在末影回响仪器上，
 * 能力只有一项普通近战攻击，见 {@link #registerGoals()}；也不给经验，免得变成刷分对象。
 * 唯一单独补上的一条是「不会发出振动」，见 {@link #dampensVibrations()}。
 *
 * <p>目标从徘徊者继承过来后它会进入愤怒：姿势像末影人那样把头往下沉，叫声变成末影人的尖叫，
 * 见 {@link #setTarget(LivingEntity)} 与 {@link #isAngry()}。
 *
 * <p>骨架与贴图是 {@code wander_shadow} 那一份，骨骼只有身体、头、双臂、双腿
 * （没有瞳孔、眼皮这些眼睛相关的骨骼，所以也没有眼珠），姿势沿用徘徊者的
 * {@code EndermanVanillaAnimator}。
 */
public class WanderShadowEntity extends Monster implements GeoEntity {

    /** 残影的存活时间（刻）。 */
    public static final int LIFETIME_TICKS = 100;

    /** 消失时扬起的粒子数量。 */
    private static final int VANISH_PARTICLES = 20;

    /** 愤怒状态，与末影人的 {@code DATA_CREEPY} 同义：有目标就是 true，见 {@link #setTarget(LivingEntity)}。 */
    private static final EntityDataAccessor<Boolean> DATA_ANGRY =
            SynchedEntityData.defineId(WanderShadowEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 剩余存活时间（刻）。 */
    private int lifeTicks = LIFETIME_TICKS;

    public WanderShadowEntity(EntityType<? extends WanderShadowEntity> entityType, Level level) {
        super(entityType, level);
        // 残影不给经验
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANGRY, false);
    }

    /**
     * 愤怒状态：与末影人的 {@code DATA_CREEPY} 同义，有目标就进入愤怒，目标清空就退出。
     *
     * <p>末影人就是在 {@code setTarget} 里切换这个状态的，残影照抄同一套语义：目标从徘徊者继承过来时
     * 立刻愤怒。状态走同步数据，客户端的姿势（愤怒时像末影人那样低头）与叫声都要用到它——
     * {@code getTarget()} 只在服务端有意义，客户端渲染拿不到。
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        this.entityData.set(DATA_ANGRY, target != null);
    }

    /** 是否处于愤怒状态，见 {@link #setTarget(LivingEntity)}。 */
    public boolean isAngry() {
        return this.entityData.get(DATA_ANGRY);
    }

    /**
     * 只有普通攻击能力：不注册任何索敌 Goal，目标由生成方（徘徊者）通过
     * {@link #setTarget(LivingEntity)} 直接交给它。
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (level().isClientSide || !isAlive()) return;
        if (--lifeTicks > 0) return;

        // 短暂时间后消失：先扬一层粒子再移除
        spawnVanishParticles();
        discard();
    }

    /**
     * 消失时扬起的粒子。
     *
     * <p>用的是末影人传送那一套传送粒子：残影本来就从徘徊者的瞬移里来，散场时也就和瞬移一个味道。
     * 想换成幽匿的魂粒子（{@code ParticleTypes.SCULK_SOUL}）改这一行即可。
     */
    private void spawnVanishParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                getX(), getY() + getBbHeight() / 2.0, getZ(),
                VANISH_PARTICLES, getBbWidth() / 2.0, getBbHeight() / 2.0, getBbWidth() / 2.0, 0.1);
    }

    /**
     * 不会发出振动：与徘徊者一样，脚步、叫声这些都不会变成振动事件，幽匿感测体与监守者感知不到它。
     *
     * <p>残影不是 {@code SculkMob}（见类注释），拿不到幽匿生物共用规则里那条
     * {@code Entity#dampensVibrations()}，所以这里单独覆写；语义与幽匿生物完全一致。
     */
    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 姿势完全由客户端的 VanillaAnimator / CEM 动画器计算，这里不注册关键帧动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
