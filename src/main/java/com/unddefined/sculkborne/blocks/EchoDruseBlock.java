package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.blocks.entity.EchoDruseBlockEntity;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EchoDruseBlock extends Block implements EntityBlock {
    public static final IntegerProperty GROWTH_STAGE = IntegerProperty.create("growth_stage", 1, 4);
    public EchoDruseBlock() {
        super(Properties.of()
                .lightLevel(state -> 2)
                .sound(SoundType.SCULK)
                .strength(2.2f, 2.2f)
                .requiresCorrectToolForDrops()
        );
        this.registerDefaultState(this.defaultBlockState().setValue(GROWTH_STAGE, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {builder.add(GROWTH_STAGE);}

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {return new EchoDruseBlockEntity(pos, state);}

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(GROWTH_STAGE)) {
            case 2 -> Block.box(4, 0, 4, 13, 9, 13);
            case 3 -> Block.box(3, 0, 3, 14, 12, 14);
            case 4 -> Block.box(2, 0, 2, 15, 15, 15);
            default -> Block.box(5, 0, 5, 12, 6, 12);
        };
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return switch (state.getValue(GROWTH_STAGE)) {
            case 2 -> {
                int count = 1 + builder.getLevel().getRandom().nextInt(2);
                yield List.of(new ItemStack(Items.ECHO_SHARD, count));
            }
            case 3 -> {
                int count = 2 + builder.getLevel().getRandom().nextInt(2);
                yield List.of(new ItemStack(Items.ECHO_SHARD, count));
            }
            case 4 -> List.of(new ItemStack(ItemRegistry.ECHO_DRUSE.get()));
            default -> List.of(new ItemStack(Items.ECHO_SHARD));
        };
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {return true;}

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof EchoDruseBlockEntity echoDruseBlockEntity){
            int growthValue = echoDruseBlockEntity.getGrowthValue();
            int growthStage = state.getValue(GROWTH_STAGE);
            int maxGrowthValue = Config.ECHO_DRUSE_MAX_GROWTH_VALUE.get();

            // 增加growth_value值，模拟生长过程
            if (growthValue < maxGrowthValue) {
                int newValue = Math.min(growthValue + random.nextInt(2), maxGrowthValue);
                echoDruseBlockEntity.setGrowthValue(newValue);
            }

            // 根据growth_value更新growth_stage
            int newStage = growthStage;
            if (growthValue > maxGrowthValue * 3 / 4 && growthStage < 4) {
                newStage = 4;
            } else if (growthValue > maxGrowthValue /2 && growthStage < 3) {
                newStage = 3;
            } else if (growthValue > maxGrowthValue /4 && growthStage < 2) {
                newStage = 2;
            }

            // 如果growth_stage需要更新，则更新方块状态
            if (newStage != growthStage) {
                level.setBlock(pos, state.setValue(GROWTH_STAGE, newStage), 3);
            }
        }

    }
}