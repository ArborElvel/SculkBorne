package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.compat.clumps.ClumpsCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Map;

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
 * <p>自然生成：加入主世界全部生物群系的怪物列表，生成权重与末影人一致（10），一次 1~4 只；
 * 生成位置与概率走幽匿生物共用的判定，见
 * {@link com.unddefined.sculkborne.server.events.SculkMobSpawnPlacements}。
 *
 * <p>另外，它还有非常小的几率出现在末影回响仪器上：仪器处于加载状态时每个仪器每秒判定一次，
 * 见 {@link com.unddefined.sculkborne.compat.enderechoing.EnderEchoingDeviceHooks}。
 * 这样出现的个体只停留 {@value #DEVICE_APPEARANCE_TICKS} 刻，到点还没有攻击目标就瞬移消失，
 * 有目标则留下来照常行动，见 {@link #markDeviceAppearance()}。
 *
 * <p>另外，徘徊者会吸引经验球：{@value #XP_ORB_ATTRACTION_DISTANCE} 格内的经验球优先飞向徘徊者，
 * 并且在此期间不再跟随玩家，见 {@link #findXpOrbAttractor(ExperienceOrb)} 与
 * {@code com.unddefined.sculkborne.mixin.ExperienceOrbMixin}。
 *
 * <p>它也会抢玩家的经验：击中玩家时按 {@code wanderer_xp_steal_fraction} 的比例剥落玩家的总经验
 * （至少 {@value #XP_STEAL_MIN} 点），剥下来的经验变成经验球掉在玩家身上，见
 * {@link #stripExperienceOnHit(Player)}；这 {@value #XP_STEAL_PICKUP_DELAY} 刻里玩家捡不走球、
 * 它自己也吸不走球，球先被吸引到身边悬着，解禁后再被吸走，
 * 按 {@code wanderer_heal_per_xp} 的比例给自己回血，见 {@link #absorbExperienceOrbs()}。
 * 吸取经验回的血可以超过自己的血量上限，见 {@link Overhealable}。
 *
 * <p>掉落除幽匿生物的基础掉落外，还额外掉末影珍珠与回响碎片，见 {@link #sculkBaseLoot()}。
 *
 * <p>骨架与贴图见 {@code assets/sculkborne/geo/entity/wanderer.geo.json} 与
 * {@code assets/sculkborne/textures/entity/wanderer.png}；脸上是异瞳，右眼沿用原版末影人的瞳色、
 * 左眼是 {@code #29DFEB}，颜色取自主贴图的两格瞳孔像素，再由客户端的 {@code WandererEyesLayer}
 * 用自发光渲染类型重画瞳孔方块；瞳孔转动、上下裁剪与眨眼见 freshsculk 的 {@code WandererCemAnimator#animateEyes}；
 * 待机、行走、注视与搬运方块的姿势则是 Fresh Animations 末影人 CEM 动画的移植，
 * 见 freshsculk 的 {@code WandererCemAnimator}；
 * 触发振动与干扰传送等专属行为不在这里实现。
 */
public class WandererEntity extends EnderMan implements GeoEntity, SculkMob {

    /** 经验球会被徘徊者吸引的作用距离（格），与原版经验球跟随玩家的距离一致。 */
    public static final double XP_ORB_ATTRACTION_DISTANCE = 8.0;

    /** 吸取经验球的作用半径（格）：进入这个范围的存活经验球会被吸走。 */
    public static final double XP_ORB_ABSORB_DISTANCE = 1.5;

    /** 从末影回响仪器上出现后先停留的时间（刻）：这段时间内没有攻击目标就瞬移消失。 */
    public static final int DEVICE_APPEARANCE_TICKS = 100;

    /** 击中玩家时至少剥落 1 点经验；玩家总经验不足 1 点时有多少剥多少。 */
    public static final int XP_STEAL_MIN = 1;

    /** 掉落末影珍珠的概率，与原版末影人 0~1 个（约一半）的口径一致。 */
    public static final float ENDER_PEARL_DROP_CHANCE = 0.5F;

    /** 掉落回响碎片的概率：覆盖幽匿生物基础表里的 5%。 */
    public static final float ECHO_SHARD_DROP_CHANCE = 0.2F;

    /**
     * 被剥落的经验球在这段时间内既不能被玩家捡走、也不能被徘徊者吸走（刻）。
     *
     * <p>球是从玩家身上原样掉下来的，没有额外初速度，落在地上后靠着
     * {@link #findXpOrbAttractor(ExperienceOrb)} 那条吸引规则慢慢飘到徘徊者身边；
     * 这段时间要够它飘出玩家的拾取范围（约 1.5 格），否则解禁的一瞬间就被玩家捡回去了。
     */
    public static final int XP_STEAL_PICKUP_DELAY = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;

    /** 吸经验的解禁时刻（游戏刻）：刚剥落完经验的这段时间里先不吸球，见 {@link #stripExperienceOnHit(Player)}。 */
    private long absorbLockedUntil;

    /** 仪器出现剩余时间（刻），小于 0 表示这不是从仪器上出现的临时个体。 */
    private int deviceAppearanceTicks = -1;

    public WandererEntity(EntityType<WandererEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return EnderMan.createAttributes();
    }

    /**
     * 找出当前吸引该经验球的徘徊者：作用距离内最近的、存活的一只，没有时返回 {@code null}。
     *
     * <p>原版经验球只在 {@value #XP_ORB_ATTRACTION_DISTANCE} 格内跟随最近的玩家，
     * 这里把徘徊者排在玩家的前面：范围内有徘徊者时经验球优先飞向它，
     * 由 {@code com.unddefined.sculkborne.mixin.ExperienceOrbMixin} 每 20 刻调用一次。
     *
     * @param orb 待判定的经验球
     * @return 最近的徘徊者，作用距离内没有存活的徘徊者时返回 {@code null}
     */
    @Nullable
    public static WandererEntity findXpOrbAttractor(ExperienceOrb orb) {
        List<WandererEntity> nearby = orb.level().getEntitiesOfClass(WandererEntity.class,
                orb.getBoundingBox().inflate(XP_ORB_ATTRACTION_DISTANCE),
                wanderer -> wanderer.isAlive()
                        && wanderer.distanceToSqr(orb) <= XP_ORB_ATTRACTION_DISTANCE * XP_ORB_ATTRACTION_DISTANCE);

        WandererEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (WandererEntity wanderer : nearby) {
            double distance = wanderer.distanceToSqr(orb);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = wanderer;
            }
        }
        return nearest;
    }

    /**
     * 标记为从末影回响仪器上出现的临时个体：先停留 {@value #DEVICE_APPEARANCE_TICKS} 刻，
     * 到点还没有攻击目标就瞬移消失，见 {@link #tickDeviceAppearance()}。
     *
     * <p>由 {@code compat.enderechoing.EnderEchoingDeviceHooks} 在生成之后调用。
     */
    public void markDeviceAppearance() {
        deviceAppearanceTicks = DEVICE_APPEARANCE_TICKS;
    }

    /**
     * 推进仪器出现的停留计时。
     *
     * <p>到点时若已经有攻击目标（玩家看它、打它等都会让它进入敌对），就留下来照常行动，
     * 计时器清空后不再处理；没有目标就按末影人的传送走掉（带传送音效与粒子）再消失，
     * 也就是设计里的「短暂出现后，若没有 target，瞬移并消失」。
     */
    private void tickDeviceAppearance() {
        if (level().isClientSide || deviceAppearanceTicks < 0) return;
        if (--deviceAppearanceTicks > 0) return;

        deviceAppearanceTicks = -1;
        if (getTarget() != null) return;

        teleport();
        discard();
    }

    /**
     * 击中玩家时剥落其经验：按 {@code wanderer_xp_steal_fraction} 的比例从玩家的总经验里扣掉一部分
     * （至少 {@value #XP_STEAL_MIN} 点，玩家总经验为 0 时不剥落），扣下来的经验变成经验球掉在玩家身上。
     *
     * <p>由 {@code ServerEvents} 在 {@code LivingIncomingDamageEvent} 里对伤害来源为本类生物的伤害调用，
     * 与其它幽匿生物的击中效果（失明与失聪、幽匿侵扰）走同一条规则。
     *
     * <p>球用原版经验球掉落的方式生成在玩家脚下（{@code ExperienceOrb#award}，含原版自带的随机初速度），
     * 不给额外的初速度；同时给玩家 {@value #XP_STEAL_PICKUP_DELAY} 刻的拾取延迟，免得球刚落地就被玩家捡回去，
     * 并且自己也在这段时间内不吸球（{@link #absorbLockedUntil}）：球先掉在原地，靠
     * {@link #findXpOrbAttractor(ExperienceOrb)} 那条吸引规则飘到徘徊者身边，等解禁后再由
     * {@link #absorbExperienceOrbs()} 吸走。
     *
     * @param player 被击中的玩家
     */
    public void stripExperienceOnHit(Player player) {
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        int total = player.totalExperience;
        if (total <= 0) return;

        // 剥落量：总经验 × 比例向下取整，至少 XP_STEAL_MIN 点、最多不超过玩家的总经验
        int wanted = Math.clamp(Mth.floor(total * Config.WANDERER_XP_STEAL_FRACTION.get()), XP_STEAL_MIN, total);
        player.giveExperiencePoints(-wanted);
        // 扣经验的请求可能被其它 mod 拦下，按真正扣掉的量生成经验球
        int stolen = total - player.totalExperience;
        if (stolen <= 0) return;

        player.takeXpDelay = Math.max(player.takeXpDelay, XP_STEAL_PICKUP_DELAY);
        absorbLockedUntil = serverLevel.getGameTime() + XP_STEAL_PICKUP_DELAY;

        // 与原版生物掉落经验一样：在玩家脚下按 1/3/7/.../2477 拆成若干球掉出来，没有额外初速度
        ExperienceOrb.award(serverLevel, player.position(), stolen);
    }

    /**
     * 吸走身边的经验球，按经验给自己回血，并且可以超过自己的血量上限。
     *
     * <p>吸取半径 {@value #XP_ORB_ABSORB_DISTANCE} 格，被吸到的球直接从世界里移除；
     * 一个球提供的经验是它实际装着的总量，见 {@link ClumpsCompat#totalExperience(ExperienceOrb)}
     * （原版是 {@code value × count}，装了 Clumps 时球的 {@code value} 本身就是总量）。
     *
     * <p>刚剥落完玩家经验的一段时间内不吸球（{@value #XP_STEAL_PICKUP_DELAY} 刻），
     * 这时球会被吸引过来悬在身边，等解禁后一起吃掉。
     *
     * <p>回血量是经验 × {@code wanderer_heal_per_xp}，超过血量上限的部分由
     * {@link Overhealable#sculkborne$setHealthAboveMax(float)} 直接写进生命值，
     * 不像原版 {@code heal} 那样被夹回上限。
     */
    private void absorbExperienceOrbs() {
        if (level().isClientSide || !isAlive()) return;
        if (level().getGameTime() < absorbLockedUntil) return;

        List<ExperienceOrb> orbs = level().getEntitiesOfClass(ExperienceOrb.class,
                getBoundingBox().inflate(XP_ORB_ABSORB_DISTANCE), ExperienceOrb::isAlive);
        if (orbs.isEmpty()) return;

        int experience = 0;
        for (ExperienceOrb orb : orbs) {
            experience += ClumpsCompat.totalExperience(orb);
            orb.discard();
        }
        if (experience <= 0) return;

        float healed = experience * Config.WANDERER_HEAL_PER_XP.get().floatValue();
        ((Overhealable) this).sculkborne$setHealthAboveMax(getHealth() + healed);
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25F, 0.9F + (random.nextFloat() - random.nextFloat()) * 0.35F);
    }

    /**
     * 额外掉落：末影珍珠与回响碎片。
     *
     * <p>末影珍珠按原版末影人的口径掉（0~1 个，约 {@value #ENDER_PEARL_DROP_CHANCE} 的概率）；
     * 回响碎片本来就在幽匿生物的基础表里（5%），这里按幽匿末影人的定位覆盖成
     * {@value #ECHO_SHARD_DROP_CHANCE}。抢夺附魔照旧只提高掉落率、不增加数量，
     * 见 {@link SculkMob} 的共用约定；随身搬着的方块仍由末影人自己的
     * {@code dropCustomDeathLoot} 掉出来。
     *
     * @return 追加两条之后的掉落表
     */
    @Override
    public Map<Item, SculkLoot> sculkBaseLoot() {
        Map<Item, SculkLoot> loot = SculkMob.super.sculkBaseLoot();
        loot.put(Items.ENDER_PEARL, new SculkLoot(ENDER_PEARL_DROP_CHANCE, 1, true));
        loot.put(Items.ECHO_SHARD, new SculkLoot(ECHO_SHARD_DROP_CHANCE, 1, true));
        return loot;
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
        // 从末影回响仪器上出现的个体：短暂停留后没目标就瞬移消失
        tickDeviceAppearance();
        // 吸走身边的经验球，按经验回血（可以超过血量上限）
        absorbExperienceOrbs();
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
