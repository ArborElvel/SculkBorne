package com.unddefined.sculkborne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.unddefined.sculkborne.server.SculkSpreadSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.SculkVeinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 幽匿脉络那版 {@code attemptUseCharge} 里，把可替换方块吃成幽匿块时刷出幽匿生物。
 *
 * <p>{@code SculkVeinBlock#attemptUseCharge} 只有在 {@code attemptPlaceSculk} 真的把某个相邻方块
 * 变成 {@code minecraft:sculk} 时才返回「电荷 - 1」，这里就包住这次调用，
 * 成功时把光标所在的方块位置交给 {@link SculkSpreadSpawner} 决定要不要刷怪。
 */
@Mixin(SculkVeinBlock.class)
public class SculkVeinBlockMixin {

    @WrapOperation(method = "attemptUseCharge", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/SculkVeinBlock;attemptPlaceSculk(Lnet/minecraft/world/level/block/SculkSpreader;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"))
    private boolean sculkborne$spawnSculkMobOnSculkPlaced(SculkVeinBlock block, SculkSpreader spreader,
                                                          LevelAccessor level, BlockPos pos, RandomSource random,
                                                          Operation<Boolean> original) {
        boolean placed = original.call(block, spreader, level, pos, random);
        if (placed) SculkSpreadSpawner.onSpread(level, pos, spreader);
        return placed;
    }
}
