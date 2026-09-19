package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.entities.ai.InvestigateVibrationGoal;
import com.unddefined.sculkborne.entities.ai.SculverfishBurrowGoal;
import com.unddefined.sculkborne.entities.ai.VibrationInvestigator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 幽匿蠹虫（Sculverfish）。
 *
 * <p>基类是原版蠹虫，保留它的小体型与移动方式；作为幽匿生物实现 {@link SculkMob}，
 * 因此自动接入幽匿生物的共用约定（幽匿系方块上的回血与属性加成、阳光下的虚弱与缓慢、
 * 死亡时的幽匿绽放、基础掉落、次声波压制、攻击附带幽匿侵扰等）。
 *
 * <p>自己的行为：只生成在完整的幽匿系方块上，常态潜伏在幽匿块中并在相邻幽匿块之间移动；
 * 接收到振动时朝振动源换位，落点只取幽匿块，因此不会离开幽匿块；换位不要求两块幽匿块相邻，
 * 中间隔着石头或空气也能穿过去（见 {@link VibrationInvestigator}）；
 * 玩家进入 {@value #VIEW_RANGE} 格视野后钻出攻击，击中目标、目标丢失或地面停留超时后再钻回幽匿块，
 * 在幽匿块上受伤时同样优先钻地；这些反应共同形成打了就跑的游击循环。
 */
public class SculverfishEntity extends Silverfish implements GeoEntity, SculkMob, VibrationSystem, VibrationInvestigator {
    /** 视觉/索敌范围（格），同时用于钻出判定和原版 {@link Attributes#FOLLOW_RANGE}。 */
    public static final double VIEW_RANGE = 3.0D;

    /** 潜伏时允许在锚点周围移动/追击的半径（格）。 */
    public static final int HOME_RADIUS = 8;

    /** 同一格幽匿块里最多容纳的幽匿蠹虫数量；已经挤满的方块不会再被选作钻入目标。 */
    public static final int MAX_PER_BLOCK = 1;

    /**
     * 一次换位最多跨过的距离（格）。
     *
     * <p>幽匿区域常常是不连续的，中间隔着石头、空气；它本来就藏在方块里移动，直接穿过去即可，
     * 所以换位不要求两块幽匿块相邻。这个值同时限制了一步最远跳到哪里，避免看起来像瞬移。
     */
    private static final int MAX_RELOCATE_STEP_RADIUS = 3;

    /** 地面上最长停留时间（tick），超过后强制钻回幽匿块，形成游击循环。 */
    private static final int MAX_SURFACE_TICKS = 60;

    /** 目标丢失后经过多少 tick 钻回地下。 */
    private static final int BURROW_AFTER_TARGET_LOST_TICKS = 20;

    /** 振动接收半径（格），与原版幽匿感测体、幽匿骷髅一致。 */
    private static final int VIBRATION_LISTENER_RADIUS = 8;

    /**
     * “距离过远”的倍数：振动源离自己超过视野范围的该倍数时不去调查。
     */
    private static final double FAR_VIBRATION_RANGE_FACTOR = 5.0D;

    private static final EntityDataAccessor<Integer> DATA_BURROW_STATE =
            SynchedEntityData.defineId(SculverfishEntity.class, EntityDataSerializers.INT);

    /** 潜伏/钻出/钻回的服务器状态，客户端通过 {@link #DATA_BURROW_STATE} 读取。 */
    public enum BurrowState {
        BURROWED,
        EMERGING,
        ACTIVE,
        BURROWING;

        private static final BurrowState[] STATES = values();

        private static BurrowState byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < STATES.length ? STATES[ordinal] : ACTIVE;
        }

        private static BurrowState byName(String name) {
            for (BurrowState state : STATES) if (state.name().equals(name)) return state;
            return ACTIVE;
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculverfishVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));

    /**
     * 待调查的振动源位置，没有待调查的振动时为 {@code null}。
     *
     * <p>由 {@link SculverfishVibrationUser#onReceiveVibration} 写入；之后 {@link SculverfishBurrowGoal}
     * 在幽匿块里朝它换位，追到幽匿块内离它最近的一格（或者路被同类占住）时清空它，
     * {@link InvestigateVibrationGoal} 随之结束本次调查。
     */
    @Nullable
    private BlockPos vibrationSource;

    /** 距离下一次可以接收振动还剩多少刻，见 {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL}。 */
    private int vibrationReceiveCooldown;

    /** 当前潜伏的幽匿块；没有有效锚点时不会进入潜伏状态。 */
    @Nullable
    private BlockPos burrowAnchor;

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    /** 地面上已经停留的时间，用于强制钻回。 */
    private int surfaceTicks;

    /** 连续没有目标的 tick 数，用于目标丢失后钻回。 */
    private int ticksWithoutTarget;

    /**
     * 钻地请求，由 {@link SculverfishBurrowGoal} 消费。
     *
     * <p>在幽匿块上受伤、或攻击击中目标后置位：地面活动时立刻钻回幽匿块（脚下没有就先走到附近
     * 的幽匿块），已经潜伏时立刻在地下换位。
     */
    private boolean burrowRequested;

    /**
     * 上一次钻地尝试失败后的等待计时（tick）。
     *
     * <p>由 {@link SculverfishBurrowGoal} 在「附近找不到幽匿块」「走过去走不到」时置位：
     * 到点之前不再重新尝试接近幽匿块，避免原地反复起停（看起来就是原地抖动）。
     */
    private int burrowRetryDelay;

    /** 新生成实体首次加入世界后只初始化一次；读档实体在 NBT 读取时直接标记为已初始化。 */
    private boolean spawnInitialized;

    public SculverfishEntity(EntityType<SculverfishEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Silverfish.createAttributes().add(Attributes.FOLLOW_RANGE, VIEW_RANGE);
    }

    @Override
    protected void registerGoals() {
        // 不调用 super.registerGoals()：原版蠹虫会钻入被虫蚀方块并唤醒同类，
        // 这两条都与“只居住在幽匿块中”冲突，这里显式建立自己的 Goal 列表。
        goalSelector.addGoal(0, new SculverfishBurrowGoal(this));
        goalSelector.addGoal(1, new FloatGoal(this));
        // 接收到振动时朝振动源换位。潜伏时的地下移动仍然由 SculverfishBurrowGoal 执行，
        // 所以这条 Goal 不占用任何控制标记，只负责调查的开始与结束。
        goalSelector.addGoal(2, new InvestigateVibrationGoal<>(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROW_STATE, BurrowState.ACTIVE.ordinal());
    }

    @Override
    public void tick() {
        // 客户端按同步过来的状态对齐物理标记（服务端在状态切换时设置）。客户端不改服务端权威状态，
        // 但必须知道“它现在埋在方块里”，否则自己的重力和方块碰撞会把它顶出来，和服务端位置互相拉扯。
        if (level().isClientSide) updateBurrowPhysics(getBurrowState());
        super.tick();

        if (level() instanceof ServerLevel serverLevel) {
            if (vibrationReceiveCooldown > 0) vibrationReceiveCooldown--;
            VibrationSystem.Ticker.tick(serverLevel, vibrationData, vibrationUser);
            // 已经锁定目标时不调查振动（正在追踪的目标优先），也避免振动源一直攒在手里
            if (getTarget() != null) clearVibrationSource();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!level().isClientSide && burrowRetryDelay > 0) burrowRetryDelay--;

        if (!level().isClientSide && getBurrowState() == BurrowState.ACTIVE) {
            surfaceTicks++;
            if (getTarget() == null) ticksWithoutTarget++;
            else ticksWithoutTarget = 0;
        }

        // 阳光直射下获得虚弱与缓慢
        applySunlightDebuffs();
        // 站在幽匿系方块上时按亮度反比缓慢回血
        sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);
        // 站在幽匿系方块上时临时提高移动速度与生命上限
        tickSculkBlockBonus();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        // 受伤时优先钻地：只要身上/脚下还是幽匿块，就放下当前行为先钻回地下
        if (damaged && !level().isClientSide && isOnSculkBlock()) burrowRequested = true;
        return damaged;
    }

    /**
     * 免疫“卡在方块里”的窒息伤害。
     *
     * <p>它是钻进幽匿块里生活的生物，钻入/钻出、换位时身体短暂和方块重叠属于正常状态；
     * 潜伏期本身 {@code noPhysics} 不会受伤，这条只作为兜底，避免偶发的边角情况把它慢慢磨死。
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
    }

    /**
     * 药水效果照常生效，但不把粒子同步给客户端。
     *
     * <p>它平时藏在幽匿块里，药水旋涡粒子会从方块边缘冒出来暴露位置；发光效果本身保留
     * （发光只影响轮廓渲染，见 {@code SculverfishEntityRenderer}，轮廓是穿墙的）。
     */
    @Override
    protected void updateInvisibilityStatus() {
        super.updateInvisibilityStatus();
        removeEffectParticles();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        // 攻击击中目标后优先钻地：打一下就撤，形成打了就跑的游击循环
        if (hit && !level().isClientSide) burrowRequested = true;
        return hit;
    }

    @Override
    public boolean isPickable() {
        return getBurrowState() == BurrowState.ACTIVE && super.isPickable();
    }

    @Override
    public boolean isAttackable() {
        return getBurrowState() == BurrowState.ACTIVE && super.isAttackable();
    }

    @Override
    public boolean isPushable() {
        return getBurrowState() == BurrowState.ACTIVE && super.isPushable();
    }

    /**
     * 新生成实体首次加入世界后初始化钻地状态：世界生成的直接潜伏，其余留在原地再自己钻进去。
     *
     * <p>由 {@code ServerEvents} 监听 {@code EntityJoinLevelEvent} 调用；读档实体在
     * {@link #readAdditionalSaveData(CompoundTag)} 中已经标记为初始化，不会重新潜伏。
     */
    public void initializeSpawnIfNeeded() {
        if (spawnInitialized) return;
        spawnInitialized = true;

        // 原版会给 FOLLOW_RANGE 加随机的生成加成，这里移除以保证视觉严格不超过 4 格
        AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);

        // 世界生成出来的个体直接潜伏进最近的幽匿块；刷怪蛋、指令召唤、刷怪笼这些“看得见的生成方式”
        // 保留在生成位置上，交给钻地 Goal 自己钻进去——否则会凭空出现在方块里，看不到钻入过程。
        if (!spawnsBuried()) {
            setBurrowState(BurrowState.ACTIVE);
            return;
        }

        BlockPos anchor = findSculkAnchor(level(), blockPosition(), 2, 2);
        if (anchor != null && !isBurrowBlockFull(level(), anchor, this)) {
            setBurrowAnchor(anchor);
            setBurrowState(BurrowState.BURROWED);
            moveToBurrowCenter(anchor);
        } else setBurrowState(BurrowState.ACTIVE);

    }

    /**
     * 生成时是否直接潜伏。
     *
     * <p>只有世界生成（自然刷新、区块生成）的个体直接潜伏；刷怪蛋、指令召唤、刷怪笼等玩家可见的
     * 生成方式一律先留在原地，由钻地 Goal 自己钻进去。读档实体的生成类型由 NeoForge 从存档恢复，
     * 而且读档时已经标记过 {@code spawnInitialized}，不会走到这里。
     */
    private boolean spawnsBuried() {
        MobSpawnType spawnType = getSpawnType();
        return spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
    }

    /**
     * 指定方块里已经藏了几只同类。
     *
     * <p>「已经潜伏在这里」和「正在钻进/这一格」的都算上，避免两只同时选中同一格后叠在一起。
     * 判定用方块整格的碰撞箱，所以正在钻入、身体已经进到这一格的个体也会被算进去。
     *
     * @param except 不计入的个体（一般是调用者自己），可为 {@code null}
     */
    public static int countBurrowedIn(Level level, BlockPos pos, @Nullable SculverfishEntity except) {
        return level.getEntitiesOfClass(SculverfishEntity.class, new AABB(pos),
                other -> other != except
                        && other.getBurrowState() != BurrowState.ACTIVE
                        && (pos.equals(other.burrowAnchor) || pos.equals(other.blockPosition()))).size();
    }

    /** 这一格幽匿块是否已经挤满同类（达到 {@value #MAX_PER_BLOCK} 只）。 */
    public static boolean isBurrowBlockFull(Level level, BlockPos pos, @Nullable SculverfishEntity except) {
        return countBurrowedIn(level, pos, except) >= MAX_PER_BLOCK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("BurrowState", getBurrowState().name());
        if (burrowAnchor != null) compound.putLong("BurrowAnchor", burrowAnchor.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        spawnInitialized = true;

        if (compound.contains("BurrowAnchor")) {
            setBurrowAnchor(BlockPos.of(compound.getLong("BurrowAnchor")));
        }

        BurrowState state = BurrowState.byName(compound.getString("BurrowState"));
        if (state != BurrowState.ACTIVE) {
            BlockPos anchor = burrowAnchor;
            if (anchor == null || !isFullSculkBlock(level(), anchor)) {
                anchor = findSculkAnchor(level(), blockPosition(), 8, 4);
            }
            if (anchor != null) {
                setBurrowAnchor(anchor);
                setBurrowState(BurrowState.BURROWED);
                moveToBurrowCenter(anchor);
                return;
            }
        }

        if (burrowAnchor != null && !isFullSculkBlock(level(), burrowAnchor)) {
            burrowAnchor = null;
        }
        setBurrowState(BurrowState.ACTIVE);
        if (burrowAnchor != null) restrictTo(burrowAnchor, HOME_RADIUS);
    }

    /** 当前潜伏/钻出/钻回状态。 */
    public BurrowState getBurrowState() {
        return BurrowState.byOrdinal(entityData.get(DATA_BURROW_STATE));
    }

    /** 是否完全潜伏在幽匿块内。 */
    public boolean isBurrowed() {
        return getBurrowState() == BurrowState.BURROWED;
    }

    /**
     * 是否正藏在一格幽匿块里：只有潜伏、而且自己就在幽匿块内时才藏得住。
     *
     * <p>它换位时可以跨过不连续的幽匿区域（见 {@link #findRelocationTargets}），中间那几格并不是幽匿块，
     * 这时它就露在外面，客户端不该把它藏起来。所以客户端判断“看不看得见”用的是这里，而不是
     * {@link #isBurrowed()}；潜伏方块的幽匿块被挖掉时同理，它也会露出来。
     *
     * @return 藏在幽匿块里返回 {@code true}，否则返回 {@code false}
     */
    public boolean isHiddenInSculk() {
        return isBurrowed() && isFullSculkBlock(level(), blockPosition());
    }

    /** 当前潜伏锚点，没有有效锚点时返回 {@code null}。 */
    @Nullable
    public BlockPos getBurrowAnchor() {
        return burrowAnchor;
    }

    /** 设置潜伏锚点，并把它作为不会离开幽匿区域的限制中心。 */
    public void setBurrowAnchor(BlockPos anchor) {
        this.burrowAnchor = anchor.immutable();
        restrictTo(this.burrowAnchor, HOME_RADIUS);
    }

    /** 切换钻地状态；状态同步给客户端，并统一处理无碰撞、无重力和可选中状态。 */
    public void setBurrowState(BurrowState state) {
        entityData.set(DATA_BURROW_STATE, state.ordinal());
        applyBurrowState(state);
    }

    /** 读档或外部恢复状态后重新应用一次物理/AI 标记。 */
    public void refreshBurrowState() {
        applyBurrowState(getBurrowState());
    }

    private void applyBurrowState(BurrowState state) {
        boolean active = state == BurrowState.ACTIVE;
        updateBurrowPhysics(state);
        this.surfaceTicks = 0;
        this.ticksWithoutTarget = 0;
        if (!active) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setTarget(null);
            this.getNavigation().stop();
        } else {
            // 回到地面说明钻地请求已经处理完（成功钻出或找不到落点），不再保留
            this.burrowRequested = false;
        }
    }

    /**
     * 按钻地状态设置无碰撞与无重力。
     *
     * <p>服务端在状态切换时调用（{@link #applyBurrowState(BurrowState)}）；客户端每 tick 按同步过来的
     * 状态对齐一次——客户端不能改服务端权威状态，但必须知道“它现在埋在方块里”，否则客户端的重力和
     * 方块碰撞会把它从方块里顶出来，再被服务端位置拉回去，表现为贴着幽匿块原地抖动。
     */
    private void updateBurrowPhysics(BurrowState state) {
        boolean active = state == BurrowState.ACTIVE;
        this.noPhysics = !active;
        this.setNoGravity(!active);
    }

    /** 上一次钻地尝试失败的等待是否已经结束，由 {@link SculverfishBurrowGoal} 查询。 */
    public boolean isBurrowReady() {
        return burrowRetryDelay <= 0;
    }

    /** 记录一次钻地尝试失败，{@code ticks} 之内不再重新尝试接近幽匿块。 */
    public void delayBurrow(int ticks) {
        this.burrowRetryDelay = Math.max(this.burrowRetryDelay, ticks);
    }

    /** 地面停留超时、目标丢失或受伤是否已经满足钻回条件，由 {@link SculverfishBurrowGoal} 查询。 */
    public boolean shouldBurrow() {
        return burrowRequested
                || surfaceTicks >= MAX_SURFACE_TICKS
                || ticksWithoutTarget >= BURROW_AFTER_TARGET_LOST_TICKS;
    }

    /** 取出并清除受伤钻地请求；只在 {@link SculverfishBurrowGoal} 处理该请求时调用。 */
    public boolean consumeBurrowRequest() {
        boolean requested = burrowRequested;
        this.burrowRequested = false;
        return requested;
    }

    /**
     * 待调查的振动源位置，没有待调查的振动时返回 {@code null}，见 {@link VibrationInvestigator}。
     *
     * <p>{@link SculverfishBurrowGoal} 读取它来朝振动源换位。
     */
    @Override
    @Nullable
    public BlockPos getVibrationSource() {
        return vibrationSource;
    }

    /**
     * 结束本次调查，清空待调查的振动源。
     *
     * <p>清空后 {@link SculverfishBurrowGoal} 立刻回到随机换位，{@link InvestigateVibrationGoal} 也随之结束。
     */
    @Override
    public void clearVibrationSource() {
        vibrationSource = null;
    }

    /**
     * 开始朝振动源换位：只有潜伏在幽匿块里时才会为了振动换位。
     *
     * <p>钻出、钻回和地面活动期间都返回 {@code false}——振动不会让它离开幽匿块；换位本身由
     * {@link SculverfishBurrowGoal} 执行。
     */
    @Override
    public boolean beginVibrationInvestigation(BlockPos source) {
        return isBurrowed();
    }

    /**
     * 潜伏在幽匿块里时就还在朝振动源靠近（换位由 {@link SculverfishBurrowGoal} 执行），
     * 钻出地面后本次调查结束。
     */
    @Override
    public boolean isApproachingVibrationSource() {
        return isBurrowed();
    }

    /**
     * 从 from 出发、一次换位能够到达的幽匿块：以 from 为中心、半径 {@value #MAX_RELOCATE_STEP_RADIUS} 格以内的
     * 完整幽匿块，不含 from 自己和已经挤满同类的方块。
     *
     * <p>不要求两块幽匿块相邻：幽匿区域常常是不连续的，它本来就藏在方块里移动，
     * 中间隔着的石头或空气直接穿过去即可。
     *
     * @param from 换位的起点，一般是当前潜伏锚点
     * @return 可以换位过去的幽匿块，没有时返回空列表
     */
    public List<BlockPos> findRelocationTargets(BlockPos from) {
        List<BlockPos> targets = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double maxDistanceSqr = MAX_RELOCATE_STEP_RADIUS * MAX_RELOCATE_STEP_RADIUS;

        for (int y = -MAX_RELOCATE_STEP_RADIUS; y <= MAX_RELOCATE_STEP_RADIUS; y++) {
            for (int x = -MAX_RELOCATE_STEP_RADIUS; x <= MAX_RELOCATE_STEP_RADIUS; x++) {
                for (int z = -MAX_RELOCATE_STEP_RADIUS; z <= MAX_RELOCATE_STEP_RADIUS; z++) {
                    cursor.set(from.getX() + x, from.getY() + y, from.getZ() + z);
                    if (cursor.equals(from)) continue;
                    if (from.distSqr(cursor) > maxDistanceSqr) continue;
                    if (!isFullSculkBlock(level(), cursor)) continue;
                    if (isBurrowBlockFull(level(), cursor, this)) continue;

                    targets.add(cursor.immutable());
                }
            }
        }

        return targets;
    }

    /**
     * 朝 target 换位的下一格：在 {@link #findRelocationTargets} 给出的候选中挑一格离 target 最近的幽匿块。
     *
     * <p>候选全部是完整幽匿块，而且允许不相邻，所以幽匿蠹虫追振动时既不会离开幽匿块，也能跨过不连续的
     * 幽匿区域；没有比当前锚点更靠近 target 的候选时返回 {@code null}，表示不再靠近。
     *
     * @param target 要靠近的方块
     * @return 下一步换位的幽匿块，没有可以再靠近的幽匿块时返回 {@code null}
     */
    @Nullable
    public BlockPos findSculkStepToward(BlockPos target) {
        BlockPos anchor = burrowAnchor;
        if (anchor == null) return null;

        BlockPos step = null;
        double stepDistance = anchor.distSqr(target);
        for (BlockPos candidate : findRelocationTargets(anchor)) {
            double distance = candidate.distSqr(target);
            if (distance < stepDistance) {
                stepDistance = distance;
                step = candidate;
            }
        }

        return step;
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

    /** 视野距离（格），即钻出判定用的距离属性 {@link Attributes#FOLLOW_RANGE}，用于判断振动源是否“距离过远”。 */
    private double getViewRange() {
        return getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    /**
     * “距离过远”的判定阈值（格）：振动源离自己超过视野范围的 {@value #FAR_VIBRATION_RANGE_FACTOR} 倍就不去调查。
     *
     * <p>与振动接收范围无关，接收范围固定 {@value #VIBRATION_LISTENER_RADIUS} 格；这个阈值判断的是
     * 振动源本身离自己有多远，见 {@link SculverfishVibrationUser#onReceiveVibration}。
     */
    private double getMaxInvestigateDistance() {
        return getViewRange() * FAR_VIBRATION_RANGE_FACTOR;
    }

    /**
     * 幽匿蠹虫自己的振动接收者，与原版监守者、幽匿骷髅同构。
     *
     * <p>接收半径为 {@value #VIBRATION_LISTENER_RADIUS} 格；收到振动时先看振动源离自己多远，超过视野范围的
     * {@value #FAR_VIBRATION_RANGE_FACTOR} 倍（见 {@link #getMaxInvestigateDistance()}）就不去，否则记下振动源的位置，
     * 怎么靠过去交给 {@link InvestigateVibrationGoal} 与潜伏时的 {@link SculverfishBurrowGoal}。正在调查上一次
     * 振动时不再接收新的振动、两次接收之间也要隔
     * {@link VibrationInvestigator#VIBRATION_RECEIVE_INTERVAL} 刻，避免换位换到一半反复改目标。
     *
     * <p>与幽匿僵尸不同，这里不转发振动、也不激活幽匿共鸣方块，因此不需要覆盖
     * {@link Entity#dampensVibrations()}，自身发出的振动依旧被抑制。
     */
    private class SculverfishVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource =
                new EntityPositionSource(SculverfishEntity.this, SculverfishEntity.this.getEyeHeight());

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

            // 距离过远（超过 getMaxInvestigateDistance()）就不换位。这里判断的是振动源与自己的距离，
            // 而不是接口传进来的 distance：那是振动发生位置到自己的距离，来源本身可能远得多。
            double maxDistance = getMaxInvestigateDistance();
            if (distanceToSqr(sourcePos) > maxDistance * maxDistance) return;

            vibrationSource = BlockPos.containing(sourcePos);
            vibrationReceiveCooldown = VIBRATION_RECEIVE_INTERVAL;
        }
    }

    /** 潜伏位置 {@value #VIEW_RANGE} 格内的候选玩家：存活、非创造、非旁观者。 */
    @Nullable
    public Player findNearbyPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;

        return serverLevel.getNearestPlayer(getX(), getEyeY(), getZ(), VIEW_RANGE,
                entity -> entity instanceof Player candidate
                        && candidate.isAlive()
                        && !candidate.isCreative()
                        && !candidate.isSpectator());
    }

    /** 从指定钻出落点能否看见该玩家；落点本身是无碰撞的，射线起点不会落在方块内部。 */
    public boolean canSeeFrom(Vec3 emergePos, Player player) {
        HitResult hit = level().clip(new ClipContext(emergePos, player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * 从指定幽匿块钻出的落点：优先从上方钻出，上方被挡住时看四个侧面。
     *
     * <p>落点要容得下它，并且下方必须是实心方块（钻出来得有地方站）；不要求落点上方是空的，
     * 所以贴着别的方块也能从侧面钻出来。没有任何落点时返回 {@code null}。
     */
    @Nullable
    public Vec3 findEmergePos(BlockPos anchor) {
        if (!isFullSculkBlock(level(), anchor)) return null;

        Vec3 top = emergencePos(anchor);
        if (canFitAt(top)) return top;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = anchor.relative(direction);
            if (!isSolidBlock(level(), side.below())) continue;

            Vec3 sidePos = sideEmergePos(anchor, direction);
            if (canFitAt(sidePos)) return sidePos;
        }

        return null;
    }

    /** 落点是否容得下它（不与方块碰撞）。 */
    private boolean canFitAt(Vec3 pos) {
        AABB box = getDimensions(getPose()).makeBoundingBox(pos);
        return level().noBlockCollision(this, box);
    }

    /** 潜伏点在方块内部的落点；实体高度为 0.5，取方块中心避免探出。 */
    public static Vec3 burrowPos(BlockPos anchor) {
        return new Vec3(anchor.getX() + 0.5D, anchor.getY() + 0.25D, anchor.getZ() + 0.5D);
    }

    /** 钻出后站在幽匿块顶面的落点。 */
    public static Vec3 emergencePos(BlockPos anchor) {
        return new Vec3(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
    }

    /** 从 anchor 侧面钻出的落点：同一层外侧那一格的地面。 */
    public static Vec3 sideEmergePos(BlockPos anchor, Direction direction) {
        return new Vec3(anchor.getX() + 0.5D + direction.getStepX(), anchor.getY(),
                anchor.getZ() + 0.5D + direction.getStepZ());
    }

    /** 判断一个方块是否是完整实心方块（碰撞箱占满整格），用于判断能不能落脚。 */
    public static boolean isSolidBlock(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    /** 判断一个方块是否是完整的幽匿系方块，避免把没有碰撞体积的幽匿脉络当作潜伏点。 */
    public static boolean isFullSculkBlock(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return SculkMob.isSculkBlock(state) && state.isCollisionShapeFullBlock(level, pos);
    }

    /** 在 origin 附近寻找最近的完整幽匿系方块，找不到返回 {@code null}。 */
    @Nullable
    public static BlockPos findSculkAnchor(LevelReader level, BlockPos origin, int horizontalRadius, int verticalRadius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!isFullSculkBlock(level, cursor)) continue;

                    double distance = origin.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }

        return best;
    }

    private void moveToBurrowCenter(BlockPos anchor) {
        Vec3 pos = burrowPos(anchor);
        moveTo(pos.x, pos.y, pos.z, getYRot(), getXRot());
    }

    /**
     * 压低环境音音量。
     *
     * <p>只改 ambient 这一条：原版蠹虫按 {@code getSoundVolume()}（1.0）播放环境音，
     * 这里固定为 0.1，受伤、死亡、脚步、攻击等其它声音保持原样。
     */
    @Override
    public void playAmbientSound() {
      if (getBurrowState().equals(BurrowState.BURROWED)) playSound(getAmbientSound(), 0.02F, getVoicePitch());
      else super.playAmbientSound();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 姿势完全由客户端的 SculverfishCemAnimator 计算（CEM 公式本身包含待机、行走、受伤与死亡），
        // 因此不注册关键帧动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
