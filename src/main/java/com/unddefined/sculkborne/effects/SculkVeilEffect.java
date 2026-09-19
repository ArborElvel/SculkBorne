package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.entities.SculkMob;
import com.unddefined.sculkborne.server.registry.DataRegistry;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import static com.unddefined.sculkborne.Config.SCULK_VEIL_DARKNESS_DURATION;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_INTRUSION;

public class SculkVeilEffect extends MobEffect {
    private static final int DEBUFF_INTERVAL = 60;
    private static final long DARKNESS_LINE = 20 * 60 * 3;

    public SculkVeilEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4215441);
    }

    @Override
    public void onEffectAdded(LivingEntity livingEntity, int pAmplifier) {
        if (livingEntity.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            long start = livingEntity.getData(DataRegistry.SCULK_VEIL_START);
            long lastTick = livingEntity.getData(DataRegistry.SCULK_VEIL_LAST_TICK);
            long total = livingEntity.getData(DataRegistry.SCULK_VEIL_TOTAL);
            if (start >= 0) {
                total += Math.max(0, lastTick - start + 1);
                livingEntity.setData(DataRegistry.SCULK_VEIL_TOTAL, total);
            }
            livingEntity.setData(DataRegistry.SCULK_VEIL_START, now);
            livingEntity.setData(DataRegistry.SCULK_VEIL_LAST_TICK, now);
            if (total >= DARKNESS_LINE) applyPermanentDarkness(livingEntity);
        }
        // 检查实体是否发光，如果发光则不应用影匿效果
        if (livingEntity.isCurrentlyGlowing()) {
            // 直接移除刚刚添加的效果
            livingEntity.removeEffect(MobEffectRegistry.SCULK_VEIL);
            return;
        }

        var targetingCondition = TargetingConditions.forCombat().ignoreLineOfSight().
                selector(e -> (((Mob) e).getTarget() == livingEntity));

        //remove aggro from anything targeting us
        livingEntity.level().getNearbyEntities(Mob.class, targetingCondition, livingEntity, livingEntity.getBoundingBox().inflate(40D))
                .stream().filter(e -> !(e instanceof SculkMob))
                .forEach(e -> {
                    e.setTarget(null);
                    e.targetSelector.getAvailableGoals().forEach(WrappedGoal::stop);
                    e.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                });
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int pDuration, int pAmplifier) {return true;}

    @Override
    public boolean applyEffectTick(LivingEntity entity, int pAmplifier) {
//        entity.setInvisible(true);
        // 用该实体自己的效果剩余时长驱动脉冲，不在单例 effect 上保存跨实体状态
        if (entity.level() instanceof ServerLevel) {
            long now = entity.level().getGameTime();
            entity.setData(DataRegistry.SCULK_VEIL_LAST_TICK, now);
            long start = entity.getData(DataRegistry.SCULK_VEIL_START);
            long glowingTotal = entity.getData(DataRegistry.GLOWING_TOTAL);
            long glowingStart = entity.getData(DataRegistry.GLOWING_START);
            if (glowingStart >= 0) glowingTotal += now - glowingStart + 1;
            if (start >= 0 && entity.getData(DataRegistry.SCULK_VEIL_TOTAL) + now - start + 1 - glowingTotal >= DARKNESS_LINE) {
                entity.setData(DataRegistry.SCULK_VEIL_TOTAL, DARKNESS_LINE);
                applyPermanentDarkness(entity);
            }
            MobEffectInstance veil = entity.getEffect(MobEffectRegistry.SCULK_VEIL);
            if (veil != null && veil.getDuration() > 0 && veil.getDuration() % DEBUFF_INTERVAL == 0)
                applyDebuffPulse(entity, veil.getDuration());
        }
        // 检查实体是否发光，如果发光则取消影匿效果
        return !entity.isCurrentlyGlowing();
        //TODO: 半透明

    }

    private static void applyPermanentDarkness(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, Integer.MAX_VALUE, 1, false, true));
    }

    /** 随机施加两个负面效果与黑暗效果，持续时间与影匿剩余时长一致 */
    private static void applyDebuffPulse(LivingEntity entity, int remaining) {
        MobEffectInstance weakness = new MobEffectInstance(MobEffects.WEAKNESS, remaining);
        MobEffectInstance digSlowdown = new MobEffectInstance(MobEffects.DIG_SLOWDOWN, remaining);
        MobEffectInstance hunger = new MobEffectInstance(MobEffects.HUNGER, remaining);
        MobEffectInstance movementSlowdown = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, remaining);
        MobEffectInstance deafness = new MobEffectInstance(MobEffectRegistry.DEAFNESS, remaining);

        // 确保只添加两个不同的随机效果
        int firstEffectIndex = entity.getRandom().nextInt(5);
        int secondEffectIndex;
        do {
            secondEffectIndex = entity.getRandom().nextInt(5);
        } while (secondEffectIndex == firstEffectIndex);
        MobEffectInstance[] effects = {weakness, digSlowdown, hunger, movementSlowdown, deafness};
        entity.addEffect(effects[firstEffectIndex]);
        entity.addEffect(effects[secondEffectIndex]);
        entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SCULK_VEIL_DARKNESS_DURATION.get() * 20, 1));
        if(entity.getRandom().nextInt(7) == 0) entity.addEffect(new MobEffectInstance(SCULK_INTRUSION, remaining));

    }
}
