package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.server.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkBlock.class)
public class SculkBlockMixin {

    /**
     * 让幽匿低鸣体在深暗之域的幽匿斑块世界生成时自然出现。
     * 原版世界生成中每个生长位有 1/11 生成尖啸体、10/11 生成感测体；
     * 这里给低鸣体一份与尖啸体相同的权重，即 1/11 尖啸体、1/11 低鸣体、9/11 感测体。
     * 只在世界生成(幽匿蔓延)时生效，生存模式催化生长保持原版行为。
     */
    @Inject(method = "getRandomGrowthState", at = @At("HEAD"), cancellable = true)
    private void sculkborne$naturalSculkWhisperGrowth(LevelAccessor level, BlockPos pos, RandomSource random,
                                                        boolean isWorldGeneration, CallbackInfoReturnable<BlockState> cir) {
        BlockState state;
        switch (random.nextInt(isWorldGeneration ? 10 : 5)) {
            case 0 -> state = Blocks.SCULK_SHRIEKER.defaultBlockState()
                    .setValue(SculkShriekerBlock.CAN_SUMMON, isWorldGeneration || random.nextInt(7) == 0);
            case 1 -> {
                if (isWorldGeneration) state = BlockRegistry.SCULK_WHISPER.get().defaultBlockState();
                else state = Blocks.SCULK_SHRIEKER.defaultBlockState()
                        .setValue(SculkShriekerBlock.CAN_SUMMON, random.nextInt(7) == 0);
            }
            default -> state = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        // 与原版一致：支持水方块状态的生长物在含水空间中保持 waterlogged
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty())
            state = state.setValue(BlockStateProperties.WATERLOGGED, true);

        cir.setReturnValue(state);
    }
}
