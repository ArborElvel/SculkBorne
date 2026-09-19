package com.unddefined.sculkborne.server.registry;

import com.unddefined.sculkborne.entities.CreesperEntity;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import com.unddefined.sculkborne.entities.SculkSpreaderEntity;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.unddefined.sculkborne.SculkBorne.MODID;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SculkSpreaderEntity>> SCULK_SPREADER_ENTITY =
            ENTITIES.register("sculk_spreader_entity", () -> EntityType.Builder.of(SculkSpreaderEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.4F).clientTrackingRange(16).updateInterval(2).build("sculk_spreader_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<SculkZombieEntity>> SCULK_ZOMBIE_ENTITY =
            ENTITIES.register("sculk_zombie_entity", () -> EntityType.Builder.of(SculkZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("sculk_zombie_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<CreesperEntity>> CREESPER_ENTITY =
            ENTITIES.register("creesper_entity", () -> EntityType.Builder.of(CreesperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F).clientTrackingRange(8).build("creesper_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<SculkSkeletonEntity>> SCULK_SKELETON_ENTITY =
            ENTITIES.register("sculk_skeleton_entity", () -> EntityType.Builder.of(SculkSkeletonEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).clientTrackingRange(8).build("sculk_skeleton_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<SculverfishEntity>> SCULVERFISH_ENTITY =
            ENTITIES.register("sculverfish_entity", () -> EntityType.Builder.of(SculverfishEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 0.5F).clientTrackingRange(8).build("sculverfish_entity"));
}
