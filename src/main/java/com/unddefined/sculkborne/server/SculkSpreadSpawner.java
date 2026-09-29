package com.unddefined.sculkborne.server;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkSpreader;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 幽匿蔓延时刷出幽匿生物。
 *
 * <p>蔓延器消耗电荷、真的在世界上放了东西的两次 {@code SculkBehaviour#attemptUseCharge} 都会走到这里：
 * 幽匿脉络那版把可替换方块吃成幽匿块时（{@code SculkVeinBlockMixin}）、
 * 幽匿块那版长出感测体/尖啸体时（{@code SculkBlockMixin}）。
 * 每次都按 {@link Config#SCULK_SPREADER_SPAWN_CHANCE} 判定一次，命中时在该位置附近刷出一只幽匿生物。
 * 幽匿催发体的绽放、幽匿侵扰主体周围的蔓延、幽匿生物死亡时的原地绽放共用同一个蔓延器，
 * 所以三处都会刷怪。
 *
 * <p>只在服务端、非世界生成的蔓延里生效：世界生成（幽匿斑块地物）用的是
 * {@link SculkSpreader#createWorldGenSpreader()}，跑在 {@code WorldGenLevel} 上，
 * 既过不了 {@link ServerLevel} 判定也过不了 {@link SculkSpreader#isWorldGeneration()} 判定；
 * 和平难度下不刷任何怪物。
 */
public final class SculkSpreadSpawner {

    /** 在蔓延位置附近寻找刷怪点的尝试次数，附近放不下就当这次没有刷怪。 */
    private static final int SPAWN_ATTEMPTS = 8;

    /** 刷怪点相对蔓延位置的水平半径（格）。 */
    private static final int SPAWN_RADIUS = 3;

    /** 刷怪点相对蔓延位置的竖直半径（格）。 */
    private static final int SPAWN_VERTICAL_RADIUS = 2;

    /**
     * 蔓延/生长成功时的刷怪判定。
     *
     * @param level    发生蔓延的维度，客户端与世界生成会在这里被挡掉
     * @param pos      光标所在的方块位置，也就是这次消耗电荷发生的幽匿块
     * @param spreader 发起这次蔓延的蔓延器
     */
    public static void onSpread(LevelAccessor level, BlockPos pos, SculkSpreader spreader) {
        if (spreader.isWorldGeneration()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getDifficulty() == Difficulty.PEACEFUL) return;

        RandomSource random = serverLevel.getRandom();
        if (random.nextFloat() >= Config.SCULK_SPREADER_SPAWN_CHANCE.get()) return;

        EntityType<? extends Mob> type = pickMob(random);
        if (type == null) return;

        BlockPos spawnPos = findSpawnPos(serverLevel, pos, type);
        if (spawnPos == null) return;

        type.spawn(serverLevel, spawnPos, MobSpawnType.EVENT);
    }

    /**
     * 随机挑一种幽匿生物，权重与自然生成表 {@code sculk_mobs.json} 一致；
     * 幽匿螨、残影这两种不自然生成的个体不在其中。
     *
     * @return 挑中的生物类型
     */
    @Nullable
    private static EntityType<? extends Mob> pickMob(RandomSource random) {
        List<WeightedEntry.Wrapper<EntityType<? extends Mob>>> table = List.of(
                WeightedEntry.wrap(EntityRegistry.SCULK_ZOMBIE_ENTITY.get(), 30),
                WeightedEntry.wrap(EntityRegistry.SCULK_SPREADER_ENTITY.get(), 5),
                WeightedEntry.wrap(EntityRegistry.CREESPER_ENTITY.get(), 20),
                WeightedEntry.wrap(EntityRegistry.SCULK_SKELETON_ENTITY.get(), 30),
                WeightedEntry.wrap(EntityRegistry.SCULVERFISH_ENTITY.get(), 20),
                WeightedEntry.wrap(EntityRegistry.SCULK_SHADE_ENTITY.get(), 20),
                WeightedEntry.wrap(EntityRegistry.WANDERER_ENTITY.get(), 10));

        return WeightedRandom.getRandomItem(random, table).map(WeightedEntry.Wrapper::data).orElse(null);
    }

    /**
     * 在蔓延位置附近找一个放得下该生物的位置。
     *
     * @return 找到的位置；附近都是悬空、水或太挤时返回 {@code null}
     */
    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, BlockPos center, EntityType<? extends Mob> type) {
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            cursor.set(
                    center.getX() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS,
                    center.getY() + random.nextInt(SPAWN_VERTICAL_RADIUS * 2 + 1) - SPAWN_VERTICAL_RADIUS,
                    center.getZ() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);

            if (canSpawnAt(level, cursor, type)) return cursor.immutable();
        }

        return null;
    }

    /** 落点是否可用：脚下是能站住的支撑面，且生物的碰撞箱与方块不重叠。 */
    private static boolean canSpawnAt(ServerLevel level, BlockPos pos, EntityType<? extends Mob> type) {
        if (!level.isLoaded(pos)) return false;

        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;

        return level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    private SculkSpreadSpawner() {}
}
