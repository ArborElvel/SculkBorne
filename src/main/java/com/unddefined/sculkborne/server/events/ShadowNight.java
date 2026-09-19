package com.unddefined.sculkborne.server.events;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;

/** 幽影之夜：主世界新月期间的整段夜晚。 */
@EventBusSubscriber(modid = SculkBorne.MODID)
public final class ShadowNight {
    /** 幽影之夜中幻翼的生成权重；修改此值即可调整相对其它怪物的权重。 */
    public static final int PHANTOM_SPAWN_WEIGHT = 200;
    /** 幽影之夜其它怪物的权重倍率；0.25 表示降为原来的四分之一。 */
    public static final float OTHER_MOB_WEIGHT_FACTOR = 0.85F;

    private ShadowNight() {}

    /** Minecraft 月相 4 为新月。 */
    public static boolean isActive(Level level) {
        return level.dimension() == Level.OVERWORLD && isNightOfNewMoon(level);
    }

    public static boolean isActive(ServerLevelAccessor level) {
        return level.getLevel().dimension() == Level.OVERWORLD && isNightOfNewMoon(level);
    }

    private static boolean isNightOfNewMoon(LevelAccessor level) {
        long time = Math.floorMod(level.dayTime(), 24000L);
        return level.getMoonPhase() == 4
                && time >= 13000L && time < 23000L;
    }

    /** 幽影之夜临时把幻翼加入主世界的怪物生成候选列表。 */
    @SubscribeEvent
    public static void addPhantomSpawn(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevelAccessor level)
                || !isActive(level)) return;

        // 降低其它怪物的竞争权重，但不降低幽匿生物和本事件新增的幻翼。
        for (MobSpawnSettings.SpawnerData data : new ArrayList<>(event.getSpawnerDataList())) {
            if (data.type == EntityType.PHANTOM
                    || data.type == EntityRegistry.SCULK_ZOMBIE_ENTITY.get()
                    || data.type == EntityRegistry.SCULK_SPREADER_ENTITY.get()) continue;

            event.removeSpawnerData(data);
            int reducedWeight = Math.max(1, (int) (data.getWeight().asInt() * OTHER_MOB_WEIGHT_FACTOR));
            event.addSpawnerData(new MobSpawnSettings.SpawnerData(
                    data.type, reducedWeight, data.minCount, data.maxCount));
        }

        event.addSpawnerData(new MobSpawnSettings.SpawnerData(
                EntityType.PHANTOM, PHANTOM_SPAWN_WEIGHT, 1, 2));
    }
}
