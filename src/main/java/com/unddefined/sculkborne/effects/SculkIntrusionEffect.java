package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.server.registry.DataRegistry;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class SculkIntrusionEffect extends MobEffect {
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
}
