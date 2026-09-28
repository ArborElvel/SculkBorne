package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.server.registry.DataRegistry;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class SculkIntrusionEffect extends MobEffect {
    /** 玩家受伤时用自身经验直接触发扩散器的概率。 */
    private static final float HURT_TRIGGER_CHANCE = 0.05F;
    /** 每次直接触发最多消耗的经验点。 */
    private static final int HURT_TRIGGER_XP_COST = 15;

    public SculkIntrusionEffect() {
        super(MobEffectCategory.HARMFUL, 0x4215441);
    }
    @Override
    public boolean shouldApplyEffectTickThisTick(int pDuration, int pAmplifier) {return true;}
    @Override
    public void onEffectAdded(LivingEntity entity, int pAmplifier) {
        var effect = entity.getEffect(MobEffectRegistry.SCULK_INTRUSION);
        if (effect != null && effect.getDuration() > 0)
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effect.getDuration() / 3));
    }
    @Override
    public boolean applyEffectTick(LivingEntity entity, int pAmplifier) {
        if (entity.level() instanceof ServerLevel S) entity.getData(DataRegistry.SCULK_SPREADER).serverTick(S, entity);

        return true;
    }

    /** 带幽匿侵扰的玩家受伤时，按概率消耗自身经验直接触发一次扩散器。 */
    public static void tryTriggerSpreaderOnHurt(ServerPlayer player) {
        if (!player.hasEffect(MobEffectRegistry.SCULK_INTRUSION)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int cost = Math.min(player.totalExperience, HURT_TRIGGER_XP_COST);
        if (cost <= 0 || player.getRandom().nextFloat() >= HURT_TRIGGER_CHANCE) return;

        int experienceBefore = player.totalExperience;
        player.giveExperiencePoints(-cost);
        int consumed = experienceBefore - player.totalExperience;
        if (consumed <= 0) return;

        player.getData(DataRegistry.SCULK_SPREADER).absorbExperience(level, player, player.position(), consumed);
    }
}
