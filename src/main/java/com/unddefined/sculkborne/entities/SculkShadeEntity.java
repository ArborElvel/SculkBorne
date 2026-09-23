package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.entities.ai.InvestigateVibrationGoal;
import com.unddefined.sculkborne.entities.ai.StayNearCreatureGoal;
import com.unddefined.sculkborne.entities.ai.VibrationInvestigator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.monster.Vex;
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

/**
 * 幽影（Sculk Shade）。
 *
 * <p>基类是原版恼鬼（{@link Vex}），保留恼鬼的飞行与战斗行为：无重力飞行、冲向目标的自杀式冲锋、
 * 以自身为中心的随机飘移；召唤者相关的那部分行为没有召唤者时不生效，因此自然生成的个体只按普通怪物索敌。
 *
 * <p>与恼鬼不同的地方有四处：一是空手，生成时不给主手发铁剑，见
 * {@link #populateDefaultEquipmentSlots(RandomSource, DifficultyInstance)}；二是能接收振动，
 * 接到振动后朝振动源飞过去调查（与幽匿骷髅同样是半径 {@value #VIBRATION_LISTENER_RADIUS} 格、
 * 走到看得见为止，只是它靠飞行而不是寻路），见 {@link VibrationInvestigator}；三是不离群，
 * 离附近的生物超过 {@value #LEASH_DISTANCE} 格就飞回它附近，并且优先跟着非同类生物，
 * 见 {@link StayNearCreatureGoal}；四是随光照隐身——亮度不超过 {@value #HIDDEN_LIGHT_LEVEL} 时完全隐身、
 * 不低于 {@value #VISIBLE_LIGHT_LEVEL} 时完全显形，中间按亮度过渡，进入完全隐身还会先延迟
 * {@value #INVISIBLE_DELAY_TICKS} 刻再淡出，见 {@link #tickVisibility()}。
 *
 * <p>作为幽匿生物实现 {@link SculkMob}，因此自动接入幽匿生物的共用约定
 * （幽匿系方块上的回血与属性加成、阳光下的虚弱与缓慢、死亡时的幽匿绽放、基础掉落、
 * 次声波压制、攻击附带幽匿侵扰等），并且不会成为监守者的目标。
 */
public class SculkShadeEntity extends Vex implements GeoEntity, SculkMob, VibrationSystem, VibrationInvestigator {
    /** 振动接收半径（格），与原版幽匿感测体、幽匿骷髅一致。 */
    private static final int VIBRATION_LISTENER_RADIUS = 8;

    /**
     * “距离过远”的倍数：振动源离自己超过视野范围的该倍数时不去调查。
     *
     * <p>幽影的视野很小（{@link Attributes#FOLLOW_RANGE} 的取值见 {@link #createAttributes()}），
     * 这个阈值也就不会很大：只在振动源还没跑太远时才飞过去看。
     */
    private static final double FAR_VIBRATION_RANGE_FACTOR = 4.0D;

    /**
     * 调查振动时的飞行速度倍率，取原版恼鬼自己随机飘移 Goal 用的 {@code 0.25D}，
     * 也就是像平常飘过去一样去查看，而不是像冲锋那样撞过去。
     */
    private static final double INVESTIGATE_SPEED_MODIFIER = 0.25D;

    /**
     * 牵引距离（格）：离最近的生物超过这么远就飞回去，见 {@link StayNearCreatureGoal}。
     */
    private static final double LEASH_DISTANCE = 8.0D;

    /**
     * 寻找牵引生物的搜索范围（格）：比牵引距离略大，对方刚跑出
     * {@value #LEASH_DISTANCE} 格时还追得上。
     */
    private static final double LEASH_SEARCH_RANGE = 16.0D;

    /**
     * 飞回牵引边界的速度倍率，取原版恼鬼冲锋用的 {@code 1.0D}：
     * 飞得比往外跑的目标慢就永远追不回 15 格以内。
     */
    private static final double LEASH_SPEED_MODIFIER = 1.0D;

    /** 完全隐身的光照上限：亮度不超过它时目标不透明度为 0。 */
    private static final int HIDDEN_LIGHT_LEVEL = 8;

    /** 完全显形的光照下限：亮度不低于它时目标不透明度为满。 */
    private static final int VISIBLE_LIGHT_LEVEL = 12;

    /**
     * 进入完全隐身前的延迟（tick）：亮度掉进隐身档后先维持当前外观 {@value #INVISIBLE_DELAY_TICKS} 刻，
     * 再开始淡出。
     */
    private static final int INVISIBLE_DELAY_TICKS = 20 * 3;

    /** 淡入／淡出一趟占用的刻数。 */
    private static final int FADE_TICKS = 20;

    /**
     * 每刻的不透明度变化量（0~255 的档位）：按 {@value #FADE_TICKS} 刻走完一趟向上取整，
     * 这样淡入／淡出正好在 {@value #FADE_TICKS} 刻左右结束，不会比 {@code FADE_TICKS} 还慢。
     */
    private static final int FADE_STEP = (255 + FADE_TICKS - 1) / FADE_TICKS;

    /**
     * 同步给客户端的当前不透明度（0~255）：0 完全隐身、255 完全不透明。
     *
     * <p>亮度到目标值要走一个过渡、进入完全隐身还要先延迟，这段跨刻状态按本工程既有的做法放在服务端维护
     * 再同步给客户端（与幽匿僵尸的振动状态、幽匿蠹虫的潜伏状态同构），渲染器只读这个值。
     * 服务端每刻都会刷新天空光衰减，所以这里直接用 {@code getMaxLocalRawBrightness} 判断就是对的；
     * 客户端那个值只在进世界时算过一次，不能拿来判断，所以判断放在这里。
     */
    private static final EntityDataAccessor<Integer> DATA_VISIBILITY =
            SynchedEntityData.defineId(SculkShadeEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculkShadeVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));

    /**
     * 待调查的振动源位置，没有待调查的振动时为 {@code null}。
     *
     * <p>由 {@link SculkShadeVibrationUser#onReceiveVibration} 写入，由
     * {@link InvestigateVibrationGoal} 在结束调查时清空。
     */
    @Nullable
    private BlockPos vibrationSource;

    /** 距离下一次可以接收振动还剩多少刻，见 {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL}。 */
    private int vibrationReceiveCooldown;

    /** 进入完全隐身前的剩余延迟（tick），只在服务端维护。 */
    private int invisibleDelay;

    /** 是否已经按当前光照对齐过不透明度：生成／读档后的第一刻直接对齐，避免进世界先显形一下再淡出。 */
    private boolean visibilitySettled;

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    public SculkShadeEntity(EntityType<SculkShadeEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VISIBILITY, 255);
    }

    /** 客户端渲染用的当前不透明度（0~255）：0 完全隐身、255 完全不透明，见 {@link #tickVisibility()}。 */
    public int getVisibilityAlpha() {
        return entityData.get(DATA_VISIBILITY);
    }

    /**
     * 目标不透明度（0~255）：亮度不超过 {@value #HIDDEN_LIGHT_LEVEL} 时为 0、不低于
     * {@value #VISIBLE_LIGHT_LEVEL} 时为满，中间按亮度线性过渡。取光位置是本体的眼睛高度，
     * 与原版渲染实体时用的取光位置一致。
     */
    private int targetVisibilityAlpha() {
        BlockPos probe = BlockPos.containing(getX(), getY() + getEyeHeight(), getZ());
        float alpha = Mth.clamp((float) (level().getMaxLocalRawBrightness(probe) - HIDDEN_LIGHT_LEVEL)
                / (VISIBLE_LIGHT_LEVEL - HIDDEN_LIGHT_LEVEL), 0.0F, 1.0F);
        return Math.round(alpha * 255.0F);
    }

    /**
     * 推进不透明度，只在服务端调用，见 {@link #DATA_VISIBILITY}。
     *
     * <p>实际值每刻朝目标靠 {@value #FADE_STEP} 档，于是淡入与淡出都有过渡，不会因为光照跳变而突然出现
     * 或消失；目标变成完全隐身（0）时先等 {@value #INVISIBLE_DELAY_TICKS} 刻再开始淡出，也就是灯被移走时
     * 它还会在原地多留一会儿；延迟期间光照又亮起来就地取消延迟，直接开始淡入。
     */
    private void tickVisibility() {
        int target = targetVisibilityAlpha();
        int current = getVisibilityAlpha();

        // 生成／读档后的第一刻直接对齐，避免进世界先显形一下再淡出
        if (!visibilitySettled) {
            visibilitySettled = true;
            entityData.set(DATA_VISIBILITY, target);
            return;
        }

        if (target == current) {
            invisibleDelay = 0;
            return;
        }

        if (target < current) {
            // 变暗：只有目标是完全隐身时才先延迟；带宽内的变暗照样立刻开始过渡
            if (target <= 0 && invisibleDelay < INVISIBLE_DELAY_TICKS) {
                invisibleDelay++;
                return;
            }
        } else {
            invisibleDelay = 0;
        }

        int step = Math.min(FADE_STEP, Math.abs(target - current));
        entityData.set(DATA_VISIBILITY, current + Integer.signum(target - current) * step);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes().add(Attributes.FOLLOW_RANGE, 5).add(Attributes.MOVEMENT_SPEED, 0.15);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 离最近的生物超过 15 格就往回飞：优先级放在最前面，飘移与调查振动都不能把它带出这条范围
        goalSelector.addGoal(1, new StayNearCreatureGoal(this, LEASH_DISTANCE, LEASH_SEARCH_RANGE,
                LEASH_SPEED_MODIFIER));
        // 接收到振动时先飞向振动源：优先级高于恼鬼自己的随机飘移 Goal，且只在没有攻击目标时生效，
        // 因此锁定目标后仍由恼鬼自己的冲锋 Goal 负责战斗
        goalSelector.addGoal(4, new InvestigateVibrationGoal<>(this, Flag.MOVE));
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
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            if (vibrationReceiveCooldown > 0) vibrationReceiveCooldown--;
            VibrationSystem.Ticker.tick(serverLevel, vibrationData, vibrationUser);
            // 已经锁定目标时不调查振动（正在追踪的目标优先），也避免振动源一直攒在手里
            if (getTarget() != null) clearVibrationSource();
            // 按光照推进隐身／显形，见 DATA_VISIBILITY
            tickVisibility();
        }
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
     * 用于判断振动源是否进入视野。幽影的这个属性很小，所以它在很近的地方就会停止调查。
     */
    private double getViewRange() {
        return getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    /**
     * “距离过远”的判定阈值（格）：振动源离自己超过视野范围的 {@value #FAR_VIBRATION_RANGE_FACTOR} 倍就不去调查。
     *
     * <p>与振动接收范围无关，接收范围固定 {@value #VIBRATION_LISTENER_RADIUS} 格；这个阈值判断的是
     * 振动源本身离自己有多远，见 {@link SculkShadeVibrationUser#onReceiveVibration}。
     */
    private double getMaxInvestigateDistance() {
        return getViewRange() * FAR_VIBRATION_RANGE_FACTOR;
    }

    /**
     * 判断振动源是否已经进入视野范围：距离不超过自身的视野距离（{@link #getViewRange()}），
     * 且视线没有被方块挡住，判定方式与原版 {@link net.minecraft.world.entity.LivingEntity#hasLineOfSight(Entity)} 相同。
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
     * 开始朝振动源飞过去，见 {@link VibrationInvestigator#beginVibrationInvestigation(BlockPos)}。
     *
     * <p>恼鬼是飞行生物，移动完全交给自己的移动控制器——原版恼鬼的随机飘移与冲锋也都是直接设置移动目标，
     * 不走寻路，所以这里不像幽匿骷髅那样 {@code getNavigation().moveTo(...)}，而是直接把移动目标设成振动源，
     * 之后的加速、朝向与到达都由移动控制器负责。飞行途中会直接穿过方块（恼鬼在 tick 里没有碰撞），
     * 与它虚影的身份一致。
     *
     * <p>振动源已经进入视野范围就不用特意过去，直接放弃本次调查。
     */
    @Override
    public boolean beginVibrationInvestigation(BlockPos source) {
        if (isVibrationSourceVisible()) return false;

        Vec3 target = Vec3.atCenterOf(source);
        getMoveControl().setWantedPosition(target.x, target.y, target.z, INVESTIGATE_SPEED_MODIFIER);
        return true;
    }

    /**
     * 振动源还没进入视野、飞行也还没到达时继续调查，见 {@link VibrationInvestigator}。
     *
     * <p>飞到移动目标附近后控制器会自己转入等待状态，{@link net.minecraft.world.entity.ai.control.MoveControl#hasWanted()}
     * 随之变为 {@code false}，本次调查到此结束。
     */
    @Override
    public boolean isApproachingVibrationSource() {
        return !isVibrationSourceVisible() && getMoveControl().hasWanted();
    }

    /**
     * 结束调查时停下飞行。
     *
     * <p>恼鬼的移动控制器没有公开的“停下”接口，把移动目标改成自己当前位置即可：下一次控制器 tick 就会像
     * 到达目标一样转入等待状态并减速。这样中途改调查目标、或者调查途中锁定攻击目标时，都不会拖着旧的
     * 移动目标继续飞，也不会因为 {@link net.minecraft.world.entity.ai.control.MoveControl#hasWanted()}
     * 一直为真而挡着恼鬼自己的冲锋 Goal。
     */
    @Override
    public void stopVibrationInvestigation() {
        if (getMoveControl().hasWanted()) getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0D);
    }

    /**
     * 幽影自己的振动接收者，与原版监守者、幽匿骷髅同构。
     *
     * <p>接收半径为 {@value #VIBRATION_LISTENER_RADIUS} 格；收到振动时先看振动源离自己多远，
     * 超过视野范围的 {@value #FAR_VIBRATION_RANGE_FACTOR} 倍（见 {@link #getMaxInvestigateDistance()}）就不去，
     * 否则记录振动源位置，
     * 是否飞过去交给 {@link InvestigateVibrationGoal}；正在调查上一次振动时不再接收新的振动，
     * 两次接收之间也要隔 {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL} 刻。
     *
     * <p>与幽匿僵尸不同，这里不转发振动、也不激活幽匿共鸣方块，因此不需要覆盖
     * {@link Entity#dampensVibrations()}，自身发出的振动依旧被抑制。
     */
    private class SculkShadeVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = new EntityPositionSource(
                SculkShadeEntity.this, SculkShadeEntity.this.getEyeHeight());

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

            // 距离过远（超过 getMaxInvestigateDistance()）就不移动。这里判断的是振动源与自己的距离，
            // 而不是接口传进来的 distance：那是振动发生位置到自己的距离，来源本身可能远得多。
            double maxDistance = getMaxInvestigateDistance();
            if (distanceToSqr(sourcePos) > maxDistance * maxDistance) return;

            vibrationSource = BlockPos.containing(sourcePos);
            vibrationReceiveCooldown = VIBRATION_RECEIVE_INTERVAL;
        }
    }

    @Override
    protected void updateInvisibilityStatus() {
        super.updateInvisibilityStatus();
        removeEffectParticles();
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
