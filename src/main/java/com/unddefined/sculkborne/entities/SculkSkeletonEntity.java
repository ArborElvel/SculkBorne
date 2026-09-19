package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.entities.ai.InvestigateVibrationGoal;
import com.unddefined.sculkborne.entities.ai.VibrationInvestigator;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.BiConsumer;

public class SculkSkeletonEntity extends Skeleton implements GeoEntity, SculkMob, VibrationSystem, VibrationInvestigator {
    /** 振动接收半径（格），与原版幽匿感测体、幽匿僵尸一致。 */
    private static final int VIBRATION_LISTENER_RADIUS = 8;

    /**
     * “距离过远”的倍数：振动源离自己超过视野范围的该倍数时不去调查。
     */
    private static final double FAR_VIBRATION_RANGE_FACTOR = 2.0D;

    /** 调查振动时的移动速度倍率，与原版游荡 Goal 的 {@code WaterAvoidingRandomStrollGoal(this, 1.0F)} 一致。 */
    private static final double INVESTIGATE_SPEED_MODIFIER = 1.0D;

    /** 击中生物时施加失明与失聪的持续时间，单位为游戏刻（tick），即 3 秒。 */
    public static final int HIT_DEBUFF_DURATION = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculkSkeletonVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));

    /**
     * 待调查的振动源位置，没有待调查的振动时为 {@code null}。
     *
     * <p>由 {@link SculkSkeletonVibrationUser#onReceiveVibration} 写入，由
     * {@link InvestigateVibrationGoal} 在结束调查时清空。
     */
    @Nullable
    private BlockPos vibrationSource;

    /** 距离下一次可以接收振动还剩多少刻，见 {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL}。 */
    private int vibrationReceiveCooldown;

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    public SculkSkeletonEntity(EntityType<SculkSkeletonEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Skeleton.createAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 接收到振动时先走向振动源：优先级高于游荡 Goal，且只在没有攻击目标时生效，
        // 因此锁定目标后仍由 Skeleton 自己的弓 Goal 负责战斗
        goalSelector.addGoal(4, new InvestigateVibrationGoal<>(this, Flag.MOVE));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        applySunlightDebuffs();
        sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);
        tickSculkBlockBonus();
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            if (vibrationReceiveCooldown > 0) vibrationReceiveCooldown--;
            VibrationSystem.Ticker.tick(serverLevel, vibrationData, vibrationUser);
            // 已经锁定目标时不调查振动（正在追踪的目标优先），也避免振动源一直攒在手里
            if (getTarget() != null) clearVibrationSource();
        }
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        if (level() instanceof ServerLevel serverLevel) listenerConsumer.accept(dynamicVibrationListener, serverLevel);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return vibrationUser;
    }

    /** 当前待调查的振动源位置，没有待调查的振动时返回 {@code null}。 */
    @Override
    @Nullable
    public BlockPos getVibrationSource() {
        return vibrationSource;
    }

    /** 结束调查，清空待调查的振动源，见 {@link InvestigateVibrationGoal}。 */
    @Override
    public void clearVibrationSource() {
        vibrationSource = null;
    }

    /**
     * 视野距离（格），即自身锁定目标时使用的距离属性 {@link Attributes#FOLLOW_RANGE}。
     * 用于判断振动源是否进入视野。
     */
    private double getViewRange() {
        return getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    /**
     * “距离过远”的判定阈值（格）：振动源离自己超过视野范围的两倍就不去调查。
     *
     * <p>与振动接收范围无关，接收范围固定 {@value #VIBRATION_LISTENER_RADIUS} 格；这个阈值判断的是
     * 振动源本身离自己有多远，见 {@link SculkSkeletonVibrationUser#onReceiveVibration}。
     */
    private double getMaxInvestigateDistance() {
        return getViewRange() * FAR_VIBRATION_RANGE_FACTOR;
    }

    /**
     * 判断振动源是否已经进入视野范围：距离不超过自身的视野距离（{@link #getViewRange()}），
     * 且视线没有被方块挡住，判定方式与原版 {@link LivingEntity#hasLineOfSight(Entity)} 相同。
     *
     * @return 待调查的振动源可见返回 {@code true}，没有待调查的振动或看不见时返回 {@code false}
     */
    public boolean isVibrationSourceVisible() {
        BlockPos source = vibrationSource;
        if (source == null) return false;

        Vec3 center = Vec3.atCenterOf(source);
        double range = getViewRange();
        if (getEyePosition().distanceToSqr(center) > range * range) return false;

        return level().clip(new ClipContext(getEyePosition(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /**
     * 开始朝振动源寻路，见 {@link VibrationInvestigator#beginVibrationInvestigation(BlockPos)}。
     *
     * <p>振动源已经进入视野范围就不用特意过去，没有可行路径时也不去，两种情况都放弃本次调查。
     */
    @Override
    public boolean beginVibrationInvestigation(BlockPos source) {
        if (isVibrationSourceVisible()) return false;

        return getNavigation().moveTo(source.getX() + 0.5D, source.getY(), source.getZ() + 0.5D,
                INVESTIGATE_SPEED_MODIFIER);
    }

    /** 振动源还没进入视野、寻路也还没走完时继续调查，见 {@link VibrationInvestigator}。 */
    @Override
    public boolean isApproachingVibrationSource() {
        return !isVibrationSourceVisible() && !getNavigation().isDone();
    }

    /** 结束调查时停下寻路。 */
    @Override
    public void stopVibrationInvestigation() {
        getNavigation().stop();
    }

    /** 被阳光直射时只获得虚弱与缓慢，不像普通骷髅那样燃烧。 */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    /**
     * 击中生物时使其获得失明与失聪。
     *
     * <p>由 {@code ServerEvents} 在 {@code LivingIncomingDamageEvent} 里对伤害来源为本类生物的伤害调用，
     * 因此近战与它射出的箭都走同一条规则；重复命中相当于刷新持续时间。
     *
     * @param target 被该生物击中的生物
     */
    public void applyBlindnessAndDeafnessOnHit(LivingEntity target) {
        if (level().isClientSide) return;

        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HIT_DEBUFF_DURATION));
        target.addEffect(new MobEffectInstance(MobEffectRegistry.DEAFNESS, HIT_DEBUFF_DURATION));
    }

    /**
     * 幽匿骷髅自己的振动接收者，与原版监守者、幽匿僵尸同构。
     *
     * <p>接收半径为 {@value #VIBRATION_LISTENER_RADIUS} 格；收到振动时先看振动源离自己多远，
     * 超过两倍视野范围（见 {@link #getMaxInvestigateDistance()}）就不去，否则记录振动源位置，
     * 是否走过去交给 {@link InvestigateVibrationGoal}；正在调查上一次振动时不再接收新的振动，
     * 两次接收之间也要隔 {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL} 刻。
     *
     * 与幽匿僵尸不同，这里不转发振动、也不激活幽匿共鸣方块，因此不需要覆盖
     * {@link Entity#dampensVibrations()}，自身发出的振动依旧被抑制。
     */
    private class SculkSkeletonVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = new EntityPositionSource(
                SculkSkeletonEntity.this, SculkSkeletonEntity.this.getEyeHeight());

        @Override
        public int getListenerRadius() {
            return VIBRATION_LISTENER_RADIUS;
        }

        @Override
        public PositionSource getPositionSource() {
            return positionSource;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent,
                                           GameEvent.Context context) {
            // 正在调查上一次振动、或者上一次接收之后还没过接收间隔时，不再接收新的振动
            return !isNoAi() && isAlive() && vibrationSource == null && vibrationReceiveCooldown <= 0;
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent,
                                       @Nullable Entity entity, @Nullable Entity projectileOwner, float distance) {
            // 振动源：有来源实体时用它的位置（优先投射物的发射者），否则用振动发生的位置
            Entity source = projectileOwner != null ? projectileOwner : entity;
            Vec3 sourcePos = source != null ? source.position() : Vec3.atCenterOf(pos);

            // 距离过远（振动源离自己超过两倍视野范围）就不移动。这里判断的是振动源与自己的距离，
            // 而不是接口传进来的 distance：那是振动发生位置到自己的距离，来源本身可能远得多。
            double maxDistance = getMaxInvestigateDistance();
            if (distanceToSqr(sourcePos) > maxDistance * maxDistance) return;

            vibrationSource = BlockPos.containing(sourcePos);
            vibrationReceiveCooldown = VIBRATION_RECEIVE_INTERVAL;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 姿势完全由客户端的 SculkSkeletonCemAnimator 计算（CEM 公式本身包含待机/行走/疾跑/攻击/受伤），
        // 因此不注册关键帧动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
