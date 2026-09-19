package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.blocks.EchoDruseBlock;
import com.unddefined.sculkborne.server.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SculkPatchFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkPatchFeature.class)
public class SculkPatchFeatureMixin {

    @Inject(method = "place", at = @At(value = "RETURN"))
    private void place(FeaturePlaceContext<SculkPatchConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        // 在Sculk Catalyst放置后，有一定几率在其上方生成EchoDruseBlock
            LevelAccessor level = context.level();
            BlockPos blockPos = context.origin();
            RandomSource random = context.random();

            // 检查是否在放置Sculk Catalyst
            BlockState catalystState = level.getBlockState(blockPos);
            
            if (catalystState.is(Blocks.SCULK_CATALYST)) {
                // 有一定几率在Sculk Catalyst上方生成EchoDruseBlock
                if (random.nextFloat() <= Config.ECHO_DRUSE_GENERATION_PROBABILITY.get()) {
                    BlockPos abovePos = blockPos.above();
                    BlockState aboveState = level.getBlockState(abovePos);
                    // 检查上方是否为空气
                    if (aboveState.isAir()) {
                        // 随机选择一个生长阶段(1-4)
                        int growthStage = random.nextInt(4) + 1;
                        BlockState echoDruseState = BlockRegistry.ECHO_DRUSE.get().defaultBlockState()
                                .setValue(EchoDruseBlock.GROWTH_STAGE, growthStage);
                        
                        // 放置EchoDruseBlock
                            level.setBlock(abovePos, echoDruseState, 3);
                    }
                }
            }

    }
}