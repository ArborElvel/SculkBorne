package com.unddefined.sculkborne;

import com.mojang.logging.LogUtils;
import com.unddefined.sculkborne.client.ModSoundEvents;
import com.unddefined.sculkborne.entities.CreesperEntity;
import com.unddefined.sculkborne.entities.SculkShadeEntity;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import com.unddefined.sculkborne.entities.SculkSpreaderEntity;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import com.unddefined.sculkborne.server.events.SculkMobSpawnPlacements;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.BlockRegistry;
import com.unddefined.sculkborne.server.registry.CreativeModeTabRegistry;
import com.unddefined.sculkborne.server.registry.DataRegistry;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import com.unddefined.sculkborne.server.registry.ParticlesRegistry;
import com.unddefined.sculkborne.server.registry.PotionRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

import java.util.UUID;

@Mod(SculkBorne.MODID)
public class SculkBorne {
    public static final String MODID = "sculkborne";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final UUID ZERO_UUID = new UUID(0, 0);

    public SculkBorne(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        BlockRegistry.BLOCKS.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);
        BlockEntityRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        EntityRegistry.ENTITIES.register(modEventBus);
        CreativeModeTabRegistry.CREATIVE_MODE_TABS.register(modEventBus);
        MobEffectRegistry.MOB_EFFECTS.register(modEventBus);
        PotionRegistry.POTIONS.register(modEventBus);
        ModSoundEvents.SOUND_EVENTS.register(modEventBus);
        ParticlesRegistry.PARTICLE_TYPES.register(modEventBus);
        DataRegistry.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(SculkBorne::registerEntityAttributes);
        modEventBus.addListener(SculkMobSpawnPlacements::registerSpawnPlacements);
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(EntityRegistry.SCULK_SPREADER_ENTITY.get(), SculkSpreaderEntity.createAttributes().build());
        event.put(EntityRegistry.SCULK_ZOMBIE_ENTITY.get(), SculkZombieEntity.createAttributes().build());
        event.put(EntityRegistry.CREESPER_ENTITY.get(), CreesperEntity.createAttributes().build());
        event.put(EntityRegistry.SCULK_SKELETON_ENTITY.get(), SculkSkeletonEntity.createAttributes().build());
        event.put(EntityRegistry.SCULVERFISH_ENTITY.get(), SculverfishEntity.createAttributes().build());
        event.put(EntityRegistry.SCULK_SHADE_ENTITY.get(), SculkShadeEntity.createAttributes().build());
    }
}
