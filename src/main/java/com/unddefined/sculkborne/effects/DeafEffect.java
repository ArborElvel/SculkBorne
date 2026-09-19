package com.unddefined.sculkborne.effects;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.DEAFNESS;

@EventBusSubscriber(modid = SculkBorne.MODID)
public class DeafEffect extends MobEffect {
    public DeafEffect() {
        super(MobEffectCategory.HARMFUL, 0x696969);
    }
    @SubscribeEvent
    public static void onSoundPlay(PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) if (player.hasEffect(DEAFNESS)) event.setSound(null);
    }
}