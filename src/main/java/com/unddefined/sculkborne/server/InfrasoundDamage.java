package com.unddefined.sculkborne.server;

import com.unddefined.sculkborne.entities.SculkMob;
import com.unddefined.sculkborne.network.packet.InfrasoundParticlePacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import static com.unddefined.sculkborne.server.registry.ItemRegistry.RHYME_SHARD;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.*;
import static net.minecraft.world.effect.MobEffects.CONFUSION;
import static net.minecraft.world.item.Items.ECHO_SHARD;

public class InfrasoundDamage extends DamageSource {
    public static final ResourceKey<DamageType> INFRASOUND_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("sculkborne", "infrasound_damage"));

    public InfrasoundDamage(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, @Nullable Vec3 damageSourcePosition) {
        super(type, directEntity, causingEntity, damageSourcePosition);
    }

    public static void InfrasoundBurst(ServerLevel level, Vec3 center, float hurt_range, float affect_range, int damage, Entity causingEntity) {
        InfrasoundBurst(level, center, hurt_range, affect_range, damage, causingEntity, null);
    }

    /**
     * 与 {@link #InfrasoundBurst(ServerLevel, Vec3, float, float, int, Entity)} 相同，
     * 但额外把 {@code excluded} 排除在结算之外。
     *
     * <p>自爆的幽匿爬行者需要在自身位置发出次声波，而它此刻已经被标记为死亡，
     * 原版爆炸同样不会伤害爆源生物，因此把爆源传进来排除掉：否则它会给自己挂上
     * 次声波减益，随后自爆残留的滞留云雾又会把这些减益扩散出去。
     *
     * @param excluded 不参与本次结算的生物，可为 {@code null} 表示不排除任何生物
     */
    public static void InfrasoundBurst(ServerLevel level, Vec3 center, float hurt_range, float affect_range, int damage, Entity causingEntity, @Nullable Entity excluded) {
        // 获取范围内的所有生物实体
        var entities = level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(center, affect_range * 2, affect_range * 2, affect_range * 2),
                entity -> entity != excluded);

        for (LivingEntity entity : entities) {
            // 计算实体与中心点的距离
            double distanceSqrt = entity.position().distanceTo(center);
            boolean inHurtRange = distanceSqrt <= hurt_range;
            boolean inAffectRange = distanceSqrt <= affect_range;

            // 手持龙韵碎片的生物消耗1个碎片，免疫此次次声波爆发对其的全部影响
            if ((inHurtRange || inAffectRange) && consumeRhymeShard(entity)) continue;

            // 对在半径hurt_range范围内的生物造成真实伤害
            if (inHurtRange) {
                // 创建伤害源
                var damageTypeHolder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(INFRASOUND_DAMAGE);
                var damageSource = new InfrasoundDamage(damageTypeHolder, null, causingEntity, center);

                // 造成damage点真实伤害（忽略护甲）
                entity.hurt(damageSource, damage);
                if (entity instanceof Player player)
                    player.getWardenSpawnTracker().ifPresent(t -> t.setWarningLevel(t.getWarningLevel() - 1));
                if (entity instanceof Warden W) {
                    W.hurt(damageSource, W.getHealth() * damage / 120f);
                    // 被次声波击中的监守者有几率逃跑
                    if (W.getRandom().nextFloat() >= 0.3)
                        W.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 0);
                }
            }

            // 对在affect_range范围内的生物应用debuff效果
            if (inAffectRange) {
                // 计算持续时间 = affect_range - 与中心的距离
                int duration = (int) (affect_range - distanceSqrt);

                // 手持回响碎片的生物消耗1个碎片免疫此次debuff，但伤害仍照常结算
                if (!consumeEchoShard(entity)) {
                    // 应用多种debuff效果
                    entity.addEffect(new MobEffectInstance(ATTACK_SCATTERED, duration * 20, 1));
                    entity.addEffect(new MobEffectInstance(STAGGER, duration * 20, 1));
                    entity.addEffect(new MobEffectInstance(TINNITUS, duration * 20, 1));
                    entity.addEffect(new MobEffectInstance(CONFUSION, duration * 20, 1));
                    if (level.getRandom().nextInt(3) == 0) entity.addEffect(new MobEffectInstance(SCULK_INTRUSION, duration * 20, 1));

                    // 幽匿单位额外被次声波压制：缓慢 IV 与虚弱 IV 各 10 秒
                    if (entity instanceof SculkMob sculkMob) sculkMob.applyInfrasoundDebuffs();
                }
            }

        }

        // 向所有客户端发送粒子效果数据包
        PacketDistributor.sendToAllPlayers(new InfrasoundParticlePacket(center, affect_range, false));
    }

    // 优先消耗主手的龙韵碎片，主手没有时消耗副手的；成功消耗返回true
    private static boolean consumeRhymeShard(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (mainHand.is(RHYME_SHARD)) {
            mainHand.shrink(1);
            return true;
        }
        ItemStack offHand = entity.getOffhandItem();
        if (offHand.is(RHYME_SHARD)) {
            offHand.shrink(1);
            return true;
        }
        return false;
    }

    // 优先消耗主手的回响碎片，主手没有时消耗副手的；成功消耗返回true
    private static boolean consumeEchoShard(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (mainHand.is(ECHO_SHARD)) {
            mainHand.shrink(1);
            return true;
        }
        ItemStack offHand = entity.getOffhandItem();
        if (offHand.is(ECHO_SHARD)) {
            offHand.shrink(1);
            return true;
        }
        return false;
    }
}
