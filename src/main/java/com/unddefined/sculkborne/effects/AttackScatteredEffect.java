package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import static net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED;
import static net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET;

public class AttackScatteredEffect extends MobEffect {
    //攻击失调
    public AttackScatteredEffect() {
        super(MobEffectCategory.HARMFUL, 0x808080);
    }
    public static final ResourceLocation attack_scattered_modifier_id = ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "attack_scattered");


    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.releaseUsingItem();
        if (entity instanceof Monster monster && entity.getRandom().nextFloat()<=0.7f) {
            // 强制清除目标
            monster.setTarget(null);
            // 清除记忆中的攻击目标
            monster.setLastHurtByMob(null);
            monster.setLastHurtByPlayer(null);
            monster.getNavigation().stop();
            monster.getBrain().eraseMemory(ATTACK_TARGET);
        }
        if (entity instanceof Player player) {
            AttributeModifier modifier = new AttributeModifier(attack_scattered_modifier_id, -entity.getRandom().nextFloat(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            player.getAttribute(ATTACK_SPEED).addOrUpdateTransientModifier(modifier);
        }


        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {

        return duration % 20 == 0;
    }

}