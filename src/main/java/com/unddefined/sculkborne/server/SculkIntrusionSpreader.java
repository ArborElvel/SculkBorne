package com.unddefined.sculkborne.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 幽匿侵扰的“催化主体”：每个带效果的生物持有一个自己的 SculkSpreader，
 * 作用盒跟随主体移动，cursor 超出作用盒时保留电荷，只把 position 放回盒内。
 */
public class SculkIntrusionSpreader {
    /** 作用盒半边长（方块数），盒心跟随主体位置 */
    public static final double FOLLOW_RADIUS = 8.0;
    /** 死亡事件转化为幽匿蔓延的概率（设计稿：有几率触发） */
    public static final float TRIGGER_CHANCE = 0.6F;

    private final SculkSpreader spreader = SculkSpreader.createLevelSpreader();

    /** 以主体当前位置为心的作用盒 */
    public static AABB followBox(LivingEntity owner) {
        return AABB.ofSize(owner.position(), FOLLOW_RADIUS * 2, FOLLOW_RADIUS * 2, FOLLOW_RADIUS * 2);
    }

    /** 服务端每 tick 驱动：先把超出作用盒的 cursor 拉回盒内，再让 cursor 蔓延 */
    public void serverTick(ServerLevel level, LivingEntity owner) {
        if (spreader.getCursors().isEmpty()) return;
        AABB box = followBox(owner);
        pullEscapedCursorsBack(box);
        if (!spreader.getCursors().isEmpty()) {
            SculkBloom.tickSpreader(level, owner.blockPosition(), spreader);
            // 本次蔓延可能再次走出作用盒，同样只把 position 放回盒内
            pullEscapedCursorsBack(box);
        }
    }

    /** cursor 超出作用盒时不彻底移除：保留剩余电荷，只把 position 夹回盒内最近的方块 */
    private void pullEscapedCursorsBack(AABB box) {
        for (var cursor : List.copyOf(spreader.getCursors())) {
            if (!box.contains(cursor.getPos().getCenter())) {
                spreader.getCursors().remove(cursor);
                spreader.addCursors(clampIntoBox(cursor.getPos(), box), cursor.getCharge());
            }
        }
    }

    /** 把方块坐标夹到 AABB 内允许的整数坐标范围（与 contains 的 [min, max) 语义一致） */
    private static BlockPos clampIntoBox(BlockPos pos, AABB box) {
        int minX = (int) Math.ceil(box.minX - 0.5);
        int maxX = (int) Math.ceil(box.maxX - 0.5) - 1;
        int minY = (int) Math.ceil(box.minY - 0.5);
        int maxY = (int) Math.ceil(box.maxY - 0.5) - 1;
        int minZ = (int) Math.ceil(box.minZ - 0.5);
        int maxZ = (int) Math.ceil(box.maxZ - 0.5) - 1;
        int x = Math.clamp(pos.getX(), minX, maxX);
        int y = Math.clamp(pos.getY(), minY, maxY);
        int z = Math.clamp(pos.getZ(), minZ, maxZ);
        return new BlockPos(x, y, z);
    }

    /** ENTITY_DIE 命中主体作用盒：把死亡经验转为 cursor 电荷，并模仿催发体绽放 */
    public void absorbEntityDeath(ServerLevel level, LivingEntity owner, Vec3 deathPos, int xp) {
        spreader.addCursors(SculkBloom.bloomOrigin(deathPos), xp);
        SculkBloom.playBloomEffects(level, owner.blockPosition());
    }

    /** 死亡点监听半径内是否有可用的幽匿催发体（半径与原版 CatalystListener 一致为 8） */
    public static boolean hasUsableCatalystNearby(ServerLevel level, Vec3 deathPos) {
        double catalystRadius = 8.0;
        BlockPos center = BlockPos.containing(deathPos);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8))) {
            if (!level.getBlockState(pos).is(Blocks.SCULK_CATALYST)) continue;
            if (deathPos.distanceToSqr(pos.getCenter()) > catalystRadius * catalystRadius) continue;
            if (level.getBlockEntity(pos) instanceof SculkCatalystBlockEntity) return true;
        }
        return false;
    }

    /** 效果结束时清空未完成的 cursor */
    public void clear() {
        spreader.clear();
    }
}
