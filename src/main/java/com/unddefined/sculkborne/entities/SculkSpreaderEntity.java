package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.server.SculkBloom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class SculkSpreaderEntity extends Monster implements GeoEntity, SculkMob {
    /** 反应“身边”死亡事件的半径（格），与原版催发体的监听半径一致。 */
    public static final double NEARBY_DEATH_RADIUS = 8.0;

    /** 每次触发绽放按生命上限扣除的生命值比例。 */
    public static final float NEARBY_DEATH_HEALTH_COST = 0.05F;

    /** 生命值低于生命上限的该比例后不再触发绽放。 */
    public static final float NEARBY_DEATH_MIN_HEALTH = 0.18F;

    /** 身边死亡触发的绽放冷却时间，单位游戏刻（2 秒）。 */
    public static final int NEARBY_DEATH_COOLDOWN_TICKS = 40;

    /** 死亡时在原地留下幽匿催发体的概率。 */
    public static final float CATALYST_ON_DEATH_CHANCE = 0.25F;

    /**
     * 绽放客户端效果（整只生物换成 bloom 贴图）的持续时间，单位为游戏刻。
     *
     * <p>取的是 {@code sculk_spreader_bloom.png} 一整个循环的长度：14 帧 × frametime 8。
     * 换贴图或改动画帧数/帧时长时需要同步调整这里。
     */
    public static final int BLOOM_TICKS = 14 * 8;

    /** 是否正在播放绽放客户端效果，由服务端同步给客户端，见 {@link #isBlooming()}。 */
    private static final EntityDataAccessor<Boolean> BLOOMING =
            SynchedEntityData.defineId(SculkSpreaderEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;
    /** 身边死亡绽放的剩余冷却（tick），只由服务端计时，见 {@link #NEARBY_DEATH_COOLDOWN_TICKS}。 */
    private int nearbyDeathCooldown;
    /** 绽放客户端效果的剩余时间（tick），只由服务端计时。 */
    private int bloomTicks;

    public SculkSpreaderEntity(EntityType<SculkSpreaderEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLOOMING, false);
    }

    /**
     * 是否正在播放幽匿催发体的绽放客户端效果，即整只生物的贴图是否换成了
     * {@code sculk_spreader_bloom.png}（头上带 bloom 动画的那张）。
     *
     * <p>这是个同步值：服务端在绽放时置位，客户端跟着渲染，客户端自己不做判定。
     *
     * @return 正在绽放返回 {@code true}，否则返回 {@code false}
     */
    public boolean isBlooming() {
        return this.entityData.get(BLOOMING);
    }

    /**
     * 记录一次绽放：把贴图换成 bloom 贴图并重新计时（见 {@link #BLOOM_TICKS}），
     * 同时在头顶补一次催发体绽放的粒子，见 {@link SculkBloom#playBloomParticles(ServerLevel, Vec3)}。
     *
     * <p>粒子只补粒子、不补音效：这次绽放本身已经在死亡位置播过催发体绽放的音效，
     * 头顶再来一次会把声音叠成两遍。
     *
     * @param level 触发绽放的维度
     */
    private void startBloomEffect(ServerLevel level) {
        this.entityData.set(BLOOMING, true);
        this.bloomTicks = BLOOM_TICKS;

        // 与原版催发体一样落在方块顶面之上，这里就是碰撞箱顶端（头顶）那一点
        SculkBloom.playBloomParticles(level, new Vec3(this.getX(), this.getY() + this.getBbHeight(), this.getZ()));
    }

    @Override
    public void tick() {
        super.tick();
        // 绽放状态只在服务端计时，客户端读同步值就够了
        if (!this.level().isClientSide) {
            if (this.bloomTicks > 0 && --this.bloomTicks == 0) {
                this.entityData.set(BLOOMING, false);
            }
            if (this.nearbyDeathCooldown > 0) --this.nearbyDeathCooldown;
        }
    }

    /**
     * 散播者身边有生物死亡时，在死亡位置触发一次幽匿催发体的效果。
     *
     * <p>由 {@code ServerEvents} 在 {@code LivingDeathEvent} 里对每一次死亡调用：
     * 死亡位置周围 {@value #NEARBY_DEATH_RADIUS} 格内的每一只存活散播者都会各自触发一次，
     * 各自承担一次 {@link #NEARBY_DEATH_HEALTH_COST} 的生命值消耗。死亡者自身不算“身边”，
     * 所以散播者死亡时不会因为自己触发这条规则。
     *
     * <p>每一只散播者各自有 {@value #NEARBY_DEATH_COOLDOWN_TICKS} 刻的冷却，
     * 冷却中再次收到死亡事件不会绽放、也不会扣血。
     *
     * @param level 死亡所在维度
     * @param dead  死亡的生物，死亡位置取它的坐标
     */
    public static void onNearbyEntityDeath(ServerLevel level, LivingEntity dead) {
        Vec3 deathPos = dead.position();
        List<SculkSpreaderEntity> spreaders = level.getEntitiesOfClass(SculkSpreaderEntity.class,
                AABB.ofSize(deathPos, NEARBY_DEATH_RADIUS * 2, NEARBY_DEATH_RADIUS * 2, NEARBY_DEATH_RADIUS * 2),
                spreader -> spreader != dead && spreader.isAlive());

        for (SculkSpreaderEntity spreader : spreaders) spreader.bloomOnNearbyDeath(level, dead);
    }

    /**
     * 在死亡位置绽放一次，并扣除自身生命值。
     *
     * <p>生命值低于生命上限的 {@value #NEARBY_DEATH_MIN_HEALTH} 时不再触发，
     * 扣除最多把生命值压到生命上限的 10%，因此这条规则不会让散播者自己死掉。
     * 触发后进入 {@value #NEARBY_DEATH_COOLDOWN_TICKS} 刻的冷却，
     * 冷却结束前不会再次绽放。
     * 绽放的电荷取自死亡者的经验（不低于 {@link SculkMob#SCULK_BLOOM_MIN_CHARGE}），
     * 与幽匿生物死亡时的原地绽放共用 {@link SculkBloom}。
     *
     * @param level 死亡所在维度
     * @param dead  死亡的生物，死亡位置取它的坐标
     */
    private void bloomOnNearbyDeath(ServerLevel level, LivingEntity dead) {
        if (this.nearbyDeathCooldown > 0) return;
        if (this.getHealth() < this.getMaxHealth() * NEARBY_DEATH_MIN_HEALTH) return;

        int charge = Math.max(SCULK_BLOOM_MIN_CHARGE, dead.getExperienceReward(level, dead.getKillCredit()));
        SculkBloom.bloom(level, dead.position(), charge);
        this.startBloomEffect(level);
        this.nearbyDeathCooldown = NEARBY_DEATH_COOLDOWN_TICKS;

        this.setHealth(this.getHealth() - this.getMaxHealth() * NEARBY_DEATH_HEALTH_COST);
    }

    /**
     * 死亡时按概率在原地留下一个幽匿催发体：基础概率 {@value #CATALYST_ON_DEATH_CHANCE}，
     * 击杀者每级抢夺再提高 {@value SculkMob#LOOTING_CHANCE_PER_LEVEL}（与幽匿生物自己的掉落同一条规则）。
     *
     * <p>死亡位置放得下方块（本身可替换、脚下有支撑面）时原地放置成催发体方块，
     * 放不下（例如悬空摔死、位置被占用）时改为掉落一个催发体物品。
     *
     * <p>由 {@code ServerEvents} 在 {@code LivingDeathEvent} 里调用，此时生物尚未被移出世界，
     * {@link #blockPosition()} 就是它死亡时脚下的位置。
     *
     * @param source 致死伤害来源，用于取击杀者的抢夺等级
     */
    public void leaveCatalystOnDeath(DamageSource source) {
        if (!(this.level() instanceof ServerLevel level)) return;

        int looting = SculkMob.lootingLevel(level, source.getEntity());
        float chance = Math.min(1.0F, CATALYST_ON_DEATH_CHANCE + looting * LOOTING_CHANCE_PER_LEVEL * 2);
        if (this.getRandom().nextFloat() >= chance) return;

        BlockPos pos = this.blockPosition();
        BlockPos below = pos.below();
        if (level.getBlockState(pos).canBeReplaced()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            level.setBlock(pos, Blocks.SCULK_CATALYST.defaultBlockState(), 3);
            return;
        }

        Block.popResource(level, pos, new ItemStack(Items.SCULK_CATALYST));
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
        controllers.add(new AnimationController<>(this, "controller", 1,
                state -> {
                    SculkSpreaderEntity self = state.getAnimatable();
                    boolean moving = state.isMoving() || self.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
                    return moving
                            ? state.setAndContinue(RawAnimation.begin().thenLoop("walk"))
                            : state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
