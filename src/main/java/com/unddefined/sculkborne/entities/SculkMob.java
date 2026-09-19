package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.server.SculkBloom;
import com.unddefined.sculkborne.server.SculkIntrusionSpreader;
import com.unddefined.sculkborne.server.events.SculkMobSpawnPlacements;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import com.unddefined.sculkborne.server.registry.TagRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_INTRUSION;

/**
 * 幽匿生物（Sculk Mob）的共用约定，实现该接口的生物需要满足以下规则：
 *
 * <ol>
 *     <li>在主世界所有生物群系黑暗处小概率生成；在黑暗下的幽匿系方块附近大概率生成，
 *         深暗之域概率压低，规则见 {@link SculkMobSpawnPlacements}。</li>
 *     <li>在幽匿系方块上速度和生命值上限临时提高 10%，见 {@link #tickSculkBlockBonus()}。</li>
 *     <li>死亡时概率原地触发一次幽匿催发体的效果。</li>
 *     <li>被阳光直射时获得虚弱和缓慢效果，不会燃烧，见 {@link #applySunlightDebuffs()}。</li>
 *     <li>声波（EchoSounding）会使它们发光。</li>
 *     <li>在幽匿系方块上会缓慢回血，回血速度与亮度成反比。</li>
 *     <li>均不会发出振动，幽匿感测体与监守者等无法感知到它们，见 {@link net.minecraft.world.entity.Entity#dampensVibrations()}。</li>
 *     <li>影匿的不可选中对他们无效。</li>
 *     <li>受到次声波影响时获得缓慢 IV 与虚弱 IV，见 {@link #applyInfrasoundDebuffs()}。</li>
 *     <li>攻击生物时有一定几率使被攻击者获得幽匿侵扰，见 {@link #tryApplyIntrusionOnAttack(LivingEntity)}。</li>
 *     <li>会掉落基础掉落物 echo_shard（5%）与 sculk_matter（45%），抢夺附魔默认只影响掉落率不影响数量；
 *         基础掉落的概率、数量以及抢夺是否影响数量都在 {@link #sculkBaseLoot()} 里配置，
 *         子类可覆盖该方法追加自己的掉落物。</li>
 *     <li>不包括 {@link net.minecraft.world.entity.monster.warden.Warden}。</li>
 *     <li>Warden 不会以他们为目标。</li>
 * </ol>
 *
 * <p>以上规则中的“幽匿系方块”指方块标签 {@link TagRegistry#SCULK_BLOCKS}，
 * 具体内容见数据文件 {@code data/sculkborne/tags/block/sculk_blocks.json}。
 *
 * <p>该接口只应由 {@link LivingEntity} 的子类实现，因此默认方法内部直接按
 * {@code LivingEntity} 使用 {@code this}。
 */
public interface SculkMob {

    /** 阳光直射下施加的虚弱/缓慢的持续时间，单位为游戏刻（tick）。 */
    int SUNLIGHT_DEBUFF_DURATION = 60;

    /** 效果剩余时间低于该值时才重新施加，避免每刻刷新效果并重复同步给客户端。 */
    int SUNLIGHT_DEBUFF_REFRESH_THRESHOLD = 30;

    /** 站在幽匿系方块上时每次恢复的生命值。 */
    float SCULK_HEAL_AMOUNT = 1.0F;

    /** 亮度等级为 0 时的回血间隔（tick），实际间隔 = 该值 × (亮度等级 + 1)。 */
    int SCULK_HEAL_BASE_INTERVAL = 80;

    /** 死亡时原地触发一次幽匿催发体效果的概率。 */
    float SCULK_BLOOM_CHANCE = 0.3F;

    /** 死亡时触发绽放的最低电荷，生物自身的死亡经验更高时按经验计算。 */
    int SCULK_BLOOM_MIN_CHARGE = 5;

    /** 抢夺附魔每一级提高的掉落概率，与原版 looting 的加成一致（每级 +1%）。 */
    float LOOTING_CHANCE_PER_LEVEL = 0.01F;

    /** 站在幽匿系方块上时，移动速度与生命上限临时提高的比例（10%）。 */
    float SCULK_BLOCK_BONUS = 0.10F;

    /** 受到次声波影响时，幽匿单位获得的缓慢与虚弱的等级（IV 级对应 amplifier 3）。 */
    int INFRASOUND_DEBUFF_AMPLIFIER = 3;

    /** 受到次声波影响时，幽匿单位获得的缓慢与虚弱的持续时间，单位为游戏刻（tick）。 */
    int INFRASOUND_DEBUFF_DURATION = 160;

    /** 攻击生物时，使被攻击者获得幽匿侵扰的概率。 */
    float INTRUSION_ON_ATTACK_CHANCE = 0.1F;

    /** 攻击赋予的幽匿侵扰的持续时间，单位为游戏刻（tick）。 */
    int INTRUSION_ON_ATTACK_DURATION = 20 * 60;

    /** 幽匿系方块移动速度加成的修正器 id。 */
    ResourceLocation SCULK_BLOCK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "sculk_block_speed");

    /** 幽匿系方块生命上限加成的修正器 id。 */
    ResourceLocation SCULK_BLOCK_HEALTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "sculk_block_health");

    /**
     * 判断方块是否属于幽匿系方块，即是否带有标签 {@link TagRegistry#SCULK_BLOCKS}。
     *
     * @param state 待判定的方块状态
     * @return 属于幽匿系方块返回 {@code true}，否则返回 {@code false}
     */
    static boolean isSculkBlock(BlockState state) {
        return state.is(TagRegistry.SCULK_BLOCKS);
    }

    /**
     * 判断该生物是否位于幽匿系方块上。
     *
     * <p>脚下的支撑方块或自身所处方块带有 {@link TagRegistry#SCULK_BLOCKS} 标签即算数，
     * 后者用于幽匿脉络这类没有碰撞体积、只能与生物重叠放置的方块。
     *
     * @return 位于幽匿系方块上返回 {@code true}，否则返回 {@code false}
     */
    default boolean isOnSculkBlock() {
        LivingEntity self = (LivingEntity) this;
        return isSculkBlock(self.getBlockStateOn()) || isSculkBlock(self.level().getBlockState(self.blockPosition()));
    }

    /**
     * 当前的回血间隔（tick），与所处位置的亮度成正比：{@code 基础间隔 × (亮度等级 + 1)}，
     * 也就是回血速度与亮度成反比。
     *
     * @return 恢复 {@value #SCULK_HEAL_AMOUNT} 点生命值所需的间隔（tick）
     */
    default int sculkHealInterval() {
        LivingEntity self = (LivingEntity) this;
        int light = self.level().getMaxLocalRawBrightness(self.blockPosition());
        return SCULK_HEAL_BASE_INTERVAL * (light + 1);
    }

    /**
     * 推进幽匿系方块上的回血计时，到点时治疗 {@value #SCULK_HEAL_AMOUNT} 点生命值。
     *
     * <p>计时器按当前的回血间隔（{@link #sculkHealInterval()}）一直循环，只有到点的那一刻站在
     * 幽匿系方块上（{@link #isOnSculkBlock()}）且未满血时才真正治疗；亮度越高间隔越长，
     * 因此越亮回血越慢。
     *
     * <p>需要在子类的 {@link LivingEntity#aiStep()} 中每刻调用，并把返回值保存起来作为下一次的入参：
     * {@code sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);}
     *
     * @param cooldown 当前剩余的回血计时（tick），不小于 0
     * @return 更新后的剩余计时（tick）
     */
    default int tickSculkRegeneration(int cooldown) {
        LivingEntity self = (LivingEntity) this;
        if (self.level().isClientSide || !self.isAlive()) return 0;

        if (cooldown > 0) return cooldown - 1;

        if (this.isOnSculkBlock() && self.getHealth() < self.getMaxHealth()) self.heal(SCULK_HEAL_AMOUNT);

        return this.sculkHealInterval();
    }

    /**
     * 站在幽匿系方块上时，移动速度与生命上限临时提高 {@value #SCULK_BLOCK_BONUS} 的比例。
     *
     * <p>需要在子类的 {@link LivingEntity#aiStep()} 中每刻调用：站在幽匿系方块上
     * （{@link #isOnSculkBlock()}）时挂上加成修正器，离开方块或死亡后移除
     * （生命值超过上限时原版会自动夹回）。只修改服务端，客户端由属性同步跟随。
     */
    default void tickSculkBlockBonus() {
        LivingEntity self = (LivingEntity) this;
        // 属性只在服务端改，客户端跟随同步，避免客户端把服务端同步过来的修正器删掉
        if (self.level().isClientSide) return;

        boolean active = self.isAlive() && this.isOnSculkBlock();

        updateSculkBlockModifier(self, Attributes.MOVEMENT_SPEED, SCULK_BLOCK_SPEED_MODIFIER, active);
        updateSculkBlockModifier(self, Attributes.MAX_HEALTH, SCULK_BLOCK_HEALTH_MODIFIER, active);
    }

    /**
     * 按需挂上或移除一条幽匿系方块加成修正器，状态没有变化时不做任何事。
     *
     * @param self      要修改属性的生物
     * @param attribute 目标属性
     * @param id        修正器 id
     * @param active    是否应当处于加成状态
     */
    private static void updateSculkBlockModifier(LivingEntity self, Holder<Attribute> attribute, ResourceLocation id, boolean active) {
        AttributeInstance instance = self.getAttribute(attribute);
        if (instance == null || instance.hasModifier(id) == active) return;

        if (active) {
            instance.addTransientModifier(new AttributeModifier(id, SCULK_BLOCK_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else {
            instance.removeModifier(id);
        }
    }

    /**
     * 死亡时按概率在死亡位置原地触发一次幽匿催发体的效果。
     *
     * <p>触发时以自身的死亡经验作为电荷（不低于 {@value #SCULK_BLOOM_MIN_CHARGE}）在原地生成
     * 蔓延用的 cursor，交给 {@link SculkBloom} 逐刻推进，粒子与音效与催发体绽放一致；
     * 附近已有可用的幽匿催发体时让给它，避免同一份死亡经验被消耗两次。
     */
    default void triggerSculkBloomOnDeath() {
        LivingEntity self = (LivingEntity) this;
        if (!(self.level() instanceof ServerLevel level)) return;

        if (self.getRandom().nextFloat() >= SCULK_BLOOM_CHANCE) return;

        Vec3 deathPos = self.position();
        if (SculkIntrusionSpreader.hasUsableCatalystNearby(level, deathPos)) return;

        int charge = Math.max(SCULK_BLOOM_MIN_CHARGE, self.getExperienceReward(level, null));
        SculkBloom.bloom(level, deathPos, charge);
    }

    /**
     * 一条基础掉落：命中概率、命中时掉落的数量，以及抢夺附魔是否影响数量。
     *
     * @param chance 掉落概率（0~1），抢夺附魔会在此基础上叠加
     *               {@value #LOOTING_CHANCE_PER_LEVEL} × 等级
     * @param count  命中时掉落的基础数量
     * @param lootingAffectsCount 为 {@code true} 时抢夺附魔每级额外多掉 1 个，
     *                            为 {@code false} 时数量固定为 {@code count} 个
     */
    record SculkLoot(float chance, int count, boolean lootingAffectsCount) {
    }

    /**
     * 幽匿生物的基础掉落表：物品 → 掉落概率与数量。
     *
     * <p>子类可以覆盖本方法追加自己的掉落物，例如给幽匿僵尸加上幻翼膜：
     * <pre>{@code
     * @Override
     * public Map<Item, SculkLoot> sculkBaseLoot() {
     *     Map<Item, SculkLoot> loot = SculkMob.super.sculkBaseLoot();
     *     loot.put(Items.PHANTOM_MEMBRANE, new SculkLoot(0.05F, 1, false));
     *     return loot;
     * }
     * }</pre>
     *
     * @return 基础掉落表，键为物品、值为该物品的 {@link SculkLoot}
     */
    default Map<Item, SculkLoot> sculkBaseLoot() {
        Map<Item, SculkLoot> loot = new LinkedHashMap<>();
        loot.put(Items.ECHO_SHARD, new SculkLoot(0.05F, 1, false));
        loot.put(ItemRegistry.SCULK_MATTER.get(), new SculkLoot(0.45F, 1, false));
        return loot;
    }

    /**
     * 结算基础掉落：按概率把 {@link #sculkBaseLoot()} 中的物品加入 {@code drops}。
     *
     * <p>命中时按 {@link SculkLoot} 里配置的数量掉落；抢夺附魔按
     * {@value #LOOTING_CHANCE_PER_LEVEL} × 等级提高掉落概率（与原版 looting 的加成一致），
     * 是否额外增加数量由该条掉落的 {@link SculkLoot#lootingAffectsCount()} 决定。
     * 需要在生物死亡时于服务端调用，一般由 {@code LivingDropsEvent} 触发。
     *
     * @param level  死亡所在维度
     * @param source 致死伤害来源，用于获取击杀者的抢夺等级
     * @param drops  掉落物收集器，直接往里加入待生成的 {@link ItemEntity}
     */
    default void dropSculkMobLoot(ServerLevel level, DamageSource source, Collection<ItemEntity> drops) {
        LivingEntity self = (LivingEntity) this;
        int looting = lootingLevel(level, source.getEntity());

        for (Map.Entry<Item, SculkLoot> entry : this.sculkBaseLoot().entrySet()) {
            SculkLoot loot = entry.getValue();
            float chance = Math.min(1.0F, loot.chance() + looting * LOOTING_CHANCE_PER_LEVEL);
            if (self.getRandom().nextFloat() >= chance) continue;

            int count = loot.lootingAffectsCount() ? loot.count() + looting : loot.count();
            ItemStack stack = new ItemStack(entry.getKey(), Math.max(1, count));
            ItemEntity drop = new ItemEntity(level, self.getX(), self.getY(), self.getZ(), stack);
            drop.setDefaultPickUpDelay();
            drops.add(drop);
        }
    }

    /**
     * 取击杀者身上的抢夺附魔等级。
     *
     * @param level  维度，用于获取附魔注册表
     * @param killer 击杀者，可能为 {@code null}
     * @return 抢夺等级，没有击杀者或击杀者身上没有抢夺附魔时返回 0
     */
    private static int lootingLevel(ServerLevel level, Entity killer) {
        if (!(killer instanceof LivingEntity living)) return 0;

        var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        return EnchantmentHelper.getEnchantmentLevel(looting, living);
    }

    /**
     * 次声波对幽匿单位的压制：获得 {@link MobEffects#MOVEMENT_SLOWDOWN 缓慢 IV} 与
     * {@link MobEffects#WEAKNESS 虚弱 IV} 各 {@value #INFRASOUND_DEBUFF_DURATION} 刻，
     * 对应设计文档里「使其它幽匿单位短暂失活」这条规则。
     *
     * <p>由 {@link com.unddefined.sculkborne.server.InfrasoundDamage#InfrasoundBurst} 在结算次声波减益时
     * 对范围内的幽匿单位调用。范围判定与回响碎片、龙韵碎片的免疫判定都在调用方，这里只负责施加效果。
     */
    default void applyInfrasoundDebuffs() {
        LivingEntity self = (LivingEntity) this;
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, INFRASOUND_DEBUFF_DURATION, INFRASOUND_DEBUFF_AMPLIFIER));
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, INFRASOUND_DEBUFF_DURATION, INFRASOUND_DEBUFF_AMPLIFIER));
    }

    /**
     * 攻击生物时的侵扰：按 {@value #INTRUSION_ON_ATTACK_CHANCE} 的概率使被攻击者获得
     * {@link com.unddefined.sculkborne.server.registry.MobEffectRegistry#SCULK_INTRUSION 幽匿侵扰}，
     * 持续 {@value #INTRUSION_ON_ATTACK_DURATION} 刻。
     *
     * <p>由 {@code ServerEvents} 在 {@code LivingIncomingDamageEvent} 里对伤害来源为本类生物的伤害调用，
     * 因此近战与被算作该生物造成的伤害（例如次声波苦力怕的次声波）都会走同一条规则。
     * 攻击者自身、对目标是否已有效果的判定都不在这里处理，重复命中相当于刷新持续时间。
     *
     * @param target 被该生物攻击的生物
     */
    default void tryApplyIntrusionOnAttack(LivingEntity target) {
        LivingEntity self = (LivingEntity) this;
        if (self.level().isClientSide) return;

        if (self.getRandom().nextFloat() >= INTRUSION_ON_ATTACK_CHANCE) return;

        target.addEffect(new MobEffectInstance(SCULK_INTRUSION, INTRUSION_ON_ATTACK_DURATION));
    }

    /**
     * 判断该生物是否处于阳光直射下。
     *
     * <p>判定逻辑与原版僵尸、骷髅的白天燃烧一致：白天、光照足够、头顶能看见天空，
     * 并且身上没有水或细雪；客户端以及使用固定时间的维度一律返回 {@code false}。
     *
     * @return 处于阳光直射下返回 {@code true}，否则返回 {@code false}
     */
    default boolean isInDirectSunlight() {
        LivingEntity self = (LivingEntity) this;
        if (!self.level().isDay() || self.level().isClientSide) return false;

        if (self.getLightLevelDependentMagicValue() <= 0.5F) return false;

        if (self.isInWaterRainOrBubble() || self.isInPowderSnow || self.wasInPowderSnow) return false;

        return self.level().canSeeSky(BlockPos.containing(self.getX(), self.getEyeY(), self.getZ()));
    }

    /**
     * 被阳光直射时获得{@link MobEffects#WEAKNESS 虚弱}与
     * {@link MobEffects#MOVEMENT_SLOWDOWN 缓慢}，只施加减益、不会点燃生物。
     *
     * <p>需要在子类的 {@link LivingEntity#aiStep()} 中每刻调用。方法内部已经判断了判定条件、
     * 效果剩余时间与刷新间隔，可以安全地每刻调用。
     */
    default void applySunlightDebuffs() {
        if (!this.isInDirectSunlight()) return;

        this.applySunlightDebuff(MobEffects.WEAKNESS);
        this.applySunlightDebuff(MobEffects.MOVEMENT_SLOWDOWN);
    }

    /**
     * 在效果缺失或即将结束时补上一次减益，避免每刻都刷新效果并重复同步。
     *
     * @param effect 要施加的减益效果
     */
    private void applySunlightDebuff(Holder<MobEffect> effect) {
        LivingEntity self = (LivingEntity) this;
        MobEffectInstance active = self.getEffect(effect);
        if (active == null || active.getDuration() < SUNLIGHT_DEBUFF_REFRESH_THRESHOLD) {
            self.addEffect(new MobEffectInstance(effect, SUNLIGHT_DEBUFF_DURATION, 0));
        }
    }
}
