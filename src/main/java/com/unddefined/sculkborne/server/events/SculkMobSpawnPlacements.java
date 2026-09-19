package com.unddefined.sculkborne.server.events;

import com.unddefined.sculkborne.entities.SculkMob;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * 幽匿生物的自然生成规则。
 *
 * <p>生成条目由数据包 {@code data/sculkborne/neoforge/biome_modifier/sculk_mobs.json} 加进主世界
 * 所有生物群系（{@code #minecraft:is_overworld}）的 MONSTER 列表，具体位置与概率由
 * {@link #checkSculkMobSpawnRules} 决定。
 * 附近有幽匿系方块（{@link SculkMob#isSculkBlock}）时按 {@value #SPAWN_CHANCE_NEAR_SCULK}
 * 大概率生成，其余黑暗处只有 {@value #SPAWN_CHANCE} 的小概率。
 *
 * <p>深暗之域折中：原版该生物群系没有任何生成条目（不刷任何生物），{@code add_spawns} 会连空列表
 * 一起补上，所以那里只有幽匿生物这两个条目、没有别的怪物分摊权重。为了不把监守者的地盘填满，
 * 深暗之域的概率再乘 {@value #DEEP_DARK_CHANCE_FACTOR} 压低。
 *
 * <p>幽匿蠹虫额外要求生成位置直接位于完整的幽匿系方块上，见
 * {@link #checkSculverfishSpawnRules}；幽匿脉络这类没有完整碰撞体积的方块不能作为生成点。
 */
public class SculkMobSpawnPlacements {

    /** 附近没有幽匿系方块时的生成概率。 */
    public static final float SPAWN_CHANCE = 0.55F;

    /** 附近有幽匿系方块时的生成概率。 */
    public static final float SPAWN_CHANCE_NEAR_SCULK = 1.5F;

    /** 深暗之域的概率倍率：原版这里不刷任何生物，折中保留但明显压低。 */
    public static final float DEEP_DARK_CHANCE_FACTOR = 0.1F;

    /** 幽影之夜提高幽匿生物自然生成判定的倍率。 */
    public static final float SHADOW_NIGHT_CHANCE_FACTOR = 3.0F;

    /** 检测幽匿系方块的水平半径（方块数），竖直方向取脚下一格到头上一格。 */
    private static final int SCULK_CHECK_RADIUS = 2;

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EntityRegistry.SCULK_ZOMBIE_ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkMobSpawnPlacements::checkSculkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(EntityRegistry.SCULK_SPREADER_ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkMobSpawnPlacements::checkSculkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(EntityRegistry.CREESPER_ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkMobSpawnPlacements::checkSculkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(EntityRegistry.SCULK_SKELETON_ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkMobSpawnPlacements::checkSculkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(EntityRegistry.SCULVERFISH_ENTITY.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkMobSpawnPlacements::checkSculverfishSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * 幽匿蠹虫的自然生成判定：必须直接生成在完整的幽匿系方块上，
     * 其余黑暗、概率与深暗之域折中规则与其它幽匿生物一致。
     */
    private static <T extends Mob> boolean checkSculverfishSpawnRules(EntityType<T> type, ServerLevelAccessor level,
                                                                     MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!SculverfishEntity.isFullSculkBlock(level, pos.below())) return false;

        return checkSculkMobSpawnRules(type, level, spawnType, pos, random);
    }

    /**
     * 幽匿生物的自然生成判定。
     *
     * @return 允许在该位置生成返回 {@code true}
     */
    private static <T extends Mob> boolean checkSculkMobSpawnRules(EntityType<T> type, ServerLevelAccessor level,
                                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;

        // 与普通怪物一样需要合法的支撑方块
        if (!Mob.checkMobSpawnRules(type, level, spawnType, pos, random)) return false;

        // 刷怪笼、结构、刷怪蛋等不受"黑暗 + 小概率"限制，避免这些生成方式失效
        if (MobSpawnType.ignoresLightRequirements(spawnType)) return true;

        if (Monster.isDarkEnoughToSpawn(level, pos, random)) return false;

        float chance = hasSculkBlockNearby(level, pos) ? SPAWN_CHANCE_NEAR_SCULK : SPAWN_CHANCE;
        if (isDeepDark(level, pos)) chance *= DEEP_DARK_CHANCE_FACTOR;
        if (ShadowNight.isActive(level)) chance *= SHADOW_NIGHT_CHANCE_FACTOR;
        return random.nextFloat() < Math.min(1.0F, chance);
    }

    /** 生成位置是否位于深暗之域 */
    private static boolean isDeepDark(LevelAccessor level, BlockPos pos) {
        return level.getBiome(pos).is(Biomes.DEEP_DARK);
    }

    /** 生成位置附近（水平半径 {@value #SCULK_CHECK_RADIUS}、上下各一格）是否出现幽匿系方块 */
    private static boolean hasSculkBlockNearby(LevelAccessor level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 1; y++) {
            for (int x = -SCULK_CHECK_RADIUS; x <= SCULK_CHECK_RADIUS; x++) {
                for (int z = -SCULK_CHECK_RADIUS; z <= SCULK_CHECK_RADIUS; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (SculkMob.isSculkBlock(level.getBlockState(cursor))) return true;
                }
            }
        }
        return false;
    }
}
