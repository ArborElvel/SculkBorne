package com.unddefined.sculkborne.compat.enderechoing;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * EnderEchoing 可选兼容：使用末影回响仪器传送后，起点与终点都可能有幽匿螨出现。
 *
 * <p>幽匿螨不会自然生成（生成表与生成位置规则里都没有它），只能在 EnderEchoing 的传送装置
 * （谐振器、调谐器、折跃平台、水晶、末影回响核心）把玩家送走之后出现：
 * 出发地与目的地各按 {@link Config#SCULK_MITE_TELEPORT_SPAWN_CHANCE} 判定一次，
 * 所以一次传送可能只在起点出现、只在终点出现、两端各出现一只，也可能一只都没有。
 *
 * <p>EnderEchoing 是可选 mod，不能有编译期依赖，所以传送成功后由它那边的
 * {@code compat/sculkborne/SculkBorneBridge} 反射调用这里的
 * {@link #onTeleport(ServerPlayer, Level, Vec3)}：本类的类名与方法签名是对外契约，改名要两边一起改。
 */
public final class EnderEchoingTeleportHooks {

    /** 找落脚点的随机尝试次数，附近挤满方块时可能一次都找不到。 */
    private static final int SPAWN_ATTEMPTS = 8;

    /** 在传送端点周围寻找生成位置的水平半径（格）。 */
    private static final int SPAWN_RADIUS = 5;

    /** 在传送端点周围寻找生成位置的竖直半径（格）。 */
    private static final int SPAWN_VERTICAL_RADIUS = 2;

    /** 终点生成时与玩家保持的最小距离（格），避免直接挤进玩家身体里。 */
    private static final double MIN_PLAYER_DISTANCE = 2.0D;

    /**
     * 一次传送结束后的判定：出发地与终点各按配置的概率尝试生成一只幽匿螨。
     *
     * <p>每个端点每次传送只判定一次，由调用方（EnderEchoing 的传送装置）在传送成功后调用，
     * 因此反复踩踏触发点也不会重复判定。生成方式按事件生成（{@link MobSpawnType#EVENT}）处理，
     * 不检查亮度，也不会改变它“不自然生成”的定位。
     *
     * @param player    刚被传送的玩家，调用时已经站在终点
     * @param fromLevel 出发地所在维度（跨维度传送时与玩家当前维度不同）
     * @param fromPos   出发地坐标，即传送前玩家所在的位置
     */
    public static void onTeleport(ServerPlayer player, Level fromLevel, Vec3 fromPos) {
        // 终点：玩家现在所在的位置，生成时避开玩家自己
        if (player.level() instanceof ServerLevel destination) {
            trySpawnNear(destination, player.position(), player.blockPosition());
        }

        // 起点：传送前的位置；此时玩家已经离开，不需要避让
        if (fromLevel instanceof ServerLevel origin) {
            trySpawnNear(origin, fromPos, null);
        }
    }

    /**
     * 在 center 附近按概率生成一只幽匿螨。
     *
     * @param avoid 生成时要避开的方块位置，一般传玩家自己的位置；为 {@code null} 时不避让
     */
    private static void trySpawnNear(ServerLevel level, Vec3 center, @Nullable BlockPos avoid) {
        // 和平难度下不生成任何怪物
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;

        BlockPos centerPos = BlockPos.containing(center);
        // 起点可能已经不在加载范围内，不能为了刷怪去加载区块
        if (!level.isLoaded(centerPos)) return;

        if (level.getRandom().nextFloat() >= Config.SCULK_MITE_TELEPORT_SPAWN_CHANCE.get()) return;

        BlockPos spawnPos = findSpawnPos(level, centerPos, avoid);
        if (spawnPos == null) return;

        EntityRegistry.SCULK_MITE_ENTITY.get().spawn(level, spawnPos, MobSpawnType.EVENT);
    }

    /**
     * 在 center 附近找一个能站的位置。
     *
     * @return 找到的位置；附近都是水、悬空或太挤时返回 {@code null}
     */
    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, BlockPos center, @Nullable BlockPos avoid) {
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            cursor.set(
                    center.getX() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS,
                    center.getY() + random.nextInt(SPAWN_VERTICAL_RADIUS * 2 + 1) - SPAWN_VERTICAL_RADIUS,
                    center.getZ() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);

            if (canSpawnAt(level, cursor, avoid)) return cursor.immutable();
        }

        return null;
    }

    /**
     * 落点是否可用：脚下是能站住的完整支撑面，自身与上方一格都是空气（水与岩浆不是空气，因此会被排除），
     * 并且不和要避开的方块位置挤在一起。
     */
    private static boolean canSpawnAt(ServerLevel level, BlockPos pos, @Nullable BlockPos avoid) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return false;
        if (avoid != null && pos.closerThan(avoid, MIN_PLAYER_DISTANCE)) return false;

        return true;
    }

    private EnderEchoingTeleportHooks() {}
}
