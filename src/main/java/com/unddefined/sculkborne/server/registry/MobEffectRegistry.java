package com.unddefined.sculkborne.server.registry;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.effects.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MobEffectRegistry {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SculkBorne.MODID);
    public static final DeferredHolder<MobEffect, AttackScatteredEffect> ATTACK_SCATTERED =
            MOB_EFFECTS.register("attack_scattered", AttackScatteredEffect::new);
    public static final DeferredHolder<MobEffect, TinnitusEffect> TINNITUS = MOB_EFFECTS.register("tinnitus",
            TinnitusEffect::new);
    public static final DeferredHolder<MobEffect, StaggerEffect> STAGGER = MOB_EFFECTS.register("stagger", StaggerEffect::new);
    public static final DeferredHolder<MobEffect, DeafEffect> DEAFNESS = MOB_EFFECTS.register("deafness",
            DeafEffect::new);
    public static final DeferredHolder<MobEffect, SculkVeilEffect> SCULK_VEIL = MOB_EFFECTS.register("sculk_veil",
            SculkVeilEffect::new);
    public static final DeferredHolder<MobEffect, SculkIntrusionEffect> SCULK_INTRUSION = MOB_EFFECTS.register("sculk_intrusion",
            SculkIntrusionEffect::new);

}
