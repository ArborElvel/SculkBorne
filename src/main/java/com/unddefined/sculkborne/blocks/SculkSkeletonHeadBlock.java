package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 幽匿骷髅的头。
 *
 * <p>方块本身沿用原版头颅，行为见 {@link SculkHeadBlock}：接收振动、输出红石，但不像感测体那样
 * 共鸣相邻的振动共鸣方块。
 *
 * <p>世界生成时它会作为幽匿斑块的生长物自然出现在深暗之域（见 {@code SculkBlockMixin}），这类头带
 * {@link #NATURAL} 标记：挖掉后有几率原地变成一只幽匿骷髅；玩家自己放下的头没有这个标记，挖掉仍然掉自己。
 */
public class SculkSkeletonHeadBlock extends SculkHeadBlock {
    /** 是否由世界生成自然放下：只有自然生成的头挖掉时才判定要不要变成幽匿骷髅。 */
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");

    public SculkSkeletonHeadBlock() {
        super(SculkHeadTypes.SCULK_SKELETON, Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .instrument(NoteBlockInstrument.SKELETON)
                .strength(1.0F)
                .sound(SoundType.SCULK_SENSOR)
                .lightLevel(state -> 1)
                .pushReaction(PushReaction.DESTROY), false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NATURAL);
    }

    /** 只接收振动并推出红石，不与相邻的振动共鸣方块共鸣。 */
    @Override
    protected boolean resonatesWithAmethyst() {
        return false;
    }

    /** 自然生成的头不掉本方块：挖掉时掉什么由 {@link #playerDestroy} 决定。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(NATURAL) ? List.of() : super.getDrops(state, params);
    }

    /**
     * 自然生成的头挖掉后：有几率在原地生成一只幽匿骷髅。
     * 和平难度下不生成怪物。
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
                              ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!state.getValue(NATURAL) || !(level instanceof ServerLevel serverLevel)) return;

        boolean spawned = serverLevel.getDifficulty() != Difficulty.PEACEFUL
                && serverLevel.random.nextFloat() < Config.SCULK_SKELETON_HEAD_SPAWN_CHANCE.get()
                && EntityRegistry.SCULK_SKELETON_ENTITY.get().spawn(serverLevel, pos, MobSpawnType.TRIGGERED) != null;
        if (!spawned) Block.popResource(level, pos, new ItemStack(ItemRegistry.SCULK_SKELETON_HEAD_ITEM.get()));
    }

    @Override
    public BlockEntityType<SculkHeadBlockEntity> blockEntityType() {
        return BlockEntityRegistry.SCULK_SKELETON_HEAD.get();
    }
}
