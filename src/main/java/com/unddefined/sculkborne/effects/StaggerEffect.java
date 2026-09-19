package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.STAGGER;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;

@EventBusSubscriber(modid = SculkBorne.MODID)
public class StaggerEffect extends MobEffect {
    //踉跄
    public StaggerEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B4513); // 有害效果，棕色
    }

    public static final ResourceLocation stagger_modifier_id = ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "stagger_effect");
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 每tick应用效果
        if (!entity.onGround()) {
            RandomSource random = entity.getRandom();
            // 等级越高摔倒概率越高
            if (random.nextDouble() < 0.1 * (amplifier + 1)) {
                // 取消跳跃并施加向下的力模拟摔倒
                entity.setOnGround(false);
                entity.setDeltaMovement(entity.getDeltaMovement().add(0, -0.3, 0));
            }
        }
        final float random = entity.getRandom().nextFloat();
        if (random < 0.8f) {
            if (entity instanceof Monster monster) {
                monster.getMoveControl().strafe(
                        (entity.getRandom().nextFloat() - 0.5f) * 0.5f,
                        (entity.getRandom().nextFloat() - 0.5f) * 0.5f
                );
            }
            double x = entity.getRandom().nextDouble() * 0.3 - 0.1;
            double z = entity.getRandom().nextDouble() * 0.3 - 0.1;
            entity.setDeltaMovement(entity.getDeltaMovement().add(x, 0, z));
            if(entity.isSteppingCarefully()){
                entity.setDeltaMovement(entity.getDeltaMovement().add(0, 0.5, 0));
            }
        }
        AttributeModifier stagger_modifier = new AttributeModifier(stagger_modifier_id, -random + 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (random < 0.5f) {
            // 随机改变移动速度
            entity.getAttribute(MOVEMENT_SPEED).addOrUpdateTransientModifier(stagger_modifier);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player.hasEffect(STAGGER)) {
            // 获取当前移动输入
            var movement = event.getInput();

            // 获取效果等级（用于确定偏移程度）
            int amplifier = player.getEffect(STAGGER).getAmplifier();

            // 随机偏移移动方向
            RandomSource random = player.getRandom();
            float offsetStrength = 0.1f * (amplifier + 1); // 等级越高偏移越严重

            // 添加随机偏移
            movement.forwardImpulse += (random.nextFloat() - 0.5f) * offsetStrength;
            movement.leftImpulse += (random.nextFloat() - 0.5f) * offsetStrength;
        }

    }

}
