package com.unddefined.sculkborne.server.registry;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PotionRegistry {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, SculkBorne.MODID);
    /** 幽匿侵扰药水：基础 3 分钟 */
    public static final DeferredHolder<Potion, Potion> SCULK_INTRUSION =
            POTIONS.register("sculk_intrusion", () -> new Potion(
                    new MobEffectInstance(MobEffectRegistry.SCULK_INTRUSION, 20 * 60 * 3)
            ));
}
