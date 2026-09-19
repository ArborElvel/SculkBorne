package com.unddefined.sculkborne.blocks.entity;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.blocks.EchoDruseBlock;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EchoDruseBlockEntity extends BlockEntity {
    int growthValue = initGrowthValue();
    public int initGrowthValue(){
        return switch (getBlockState().getValue(EchoDruseBlock.GROWTH_STAGE)){
            case 2 -> MAX_GROWTH_VALUE / 4; // 25%
            case 3 -> MAX_GROWTH_VALUE / 2; // 50%
            case 4 -> MAX_GROWTH_VALUE * 3 / 4; // 75%
            default -> 0;
        };
    }
    private static final int MAX_GROWTH_VALUE = Config.ECHO_DRUSE_MAX_GROWTH_VALUE.get();

    public EchoDruseBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.ECHO_DRUSE.get(), pos, blockState);
        this.growthValue = initGrowthValue();
    }

    public int getGrowthValue() {return growthValue;}

    public void setGrowthValue(int growthValue) {
        this.growthValue = Math.max(1, Math.min(growthValue, MAX_GROWTH_VALUE));
        setChanged();
    }
}