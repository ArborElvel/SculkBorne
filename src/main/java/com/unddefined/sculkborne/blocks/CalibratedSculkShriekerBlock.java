package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.blocks.entity.CalibratedSculkShriekerBlockEntity;
import com.unddefined.sculkborne.server.InfrasoundDamage;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.BlockRegistry;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.unddefined.sculkborne.Config.SCULK_WHISPER_AFFECT_RANGE;
import static com.unddefined.sculkborne.Config.SCULK_WHISPER_COOLDOWN;
import static com.unddefined.sculkborne.Config.SCULK_WHISPER_HURT_DAMAGE;
import static com.unddefined.sculkborne.Config.SCULK_WHISPER_HURT_RANGE;

public class CalibratedSculkShriekerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.values());
    public static final BooleanProperty RCHARGED = BooleanProperty.create("redstone_charged");

    public CalibratedSculkShriekerBlock() {
        super(Properties.of()
                .noOcclusion()
                .sound(SoundType.SCULK_SHRIEKER)
                .explosionResistance(1.0F)
                .destroyTime(1.5F)
                .pushReaction(PushReaction.DESTROY)
                .dynamicShape()
                .lightLevel(state -> 3)
        );
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP).setValue(RCHARGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RCHARGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0.0D, 0.0D, 8.0D, 16.0D, 16.0D, 16.0D);
            case SOUTH -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 8.0D);
            case EAST -> Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D);
            case WEST -> Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
            case UP -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
            case DOWN -> Block.box(0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CalibratedSculkShriekerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean charged = state.getValue(RCHARGED);
        if (charged == level.hasNeighborSignal(pos)) return;
        if (!(level.getBlockEntity(pos) instanceof CalibratedSculkShriekerBlockEntity blockEntity)) return;
        if (!blockEntity.getTheItem().is(ItemRegistry.WHISPER_DRUSE)) return;
        if (charged) level.scheduleTick(pos, this, 4);
        else {
            level.setBlock(pos, state.cycle(RCHARGED), 2);
            infrasoundBurst(pos, serverLevel, blockEntity);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RCHARGED) && !level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.cycle(RCHARGED), 2);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CalibratedSculkShriekerBlockEntity shrieker)) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack slotStack = shrieker.getTheItem();
        boolean hadWhisper = slotStack.is(ItemRegistry.WHISPER_DRUSE);
        if (stack.isEmpty() && !slotStack.isEmpty()) {
            player.setItemInHand(hand, slotStack);
            shrieker.setTheItem(ItemStack.EMPTY);
            if (hadWhisper) infrasoundBurst(pos, serverLevel, shrieker);
        } else if (!stack.isEmpty() && slotStack.isEmpty()) {
            shrieker.setTheItem(stack.copy());
            stack.shrink(stack.getCount());
        } else if (ItemStack.isSameItemSameComponents(stack, slotStack)) {
            int max = stack.getItem().getDefaultMaxStackSize();
            int total = slotStack.getCount() + stack.getCount();
            slotStack.setCount(Math.min(max, total));
            stack.setCount(Math.max(0, total - max));
        } else {
            player.setItemInHand(hand, slotStack);
            shrieker.setTheItem(stack);
            if (hadWhisper) infrasoundBurst(pos, serverLevel, shrieker);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) return;
        if (level.getBlockEntity(pos) instanceof CalibratedSculkShriekerBlockEntity shrieker) {
            ItemStack stack = shrieker.getTheItem();
            if (!stack.isEmpty()) {
                if (stack.is(ItemRegistry.WHISPER_DRUSE) && level instanceof ServerLevel serverLevel) {
                    infrasoundBurst(pos, serverLevel, shrieker);
                }
                if (!stack.is(ItemRegistry.WHISPER_DRUSE)) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void infrasoundBurst(BlockPos pos, ServerLevel level, CalibratedSculkShriekerBlockEntity blockEntity) {
        float factor = 1f / Math.max(1, blockEntity.cooldownTicks);
        InfrasoundDamage.InfrasoundBurst(level, pos.getCenter(),
                Math.max(1, SCULK_WHISPER_HURT_RANGE.getAsInt() * factor),
                Math.max(1, SCULK_WHISPER_AFFECT_RANGE.getAsInt() * factor),
                Math.max(1, (int) (SCULK_WHISPER_HURT_DAMAGE.getAsInt() * factor)), null);
        blockEntity.cooldownTicks = SCULK_WHISPER_COOLDOWN.get() * 20;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(ItemRegistry.CALIBRATED_SCULK_SHRIEKER_ITEM.get()));
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> typeA, BlockEntityType<E> typeB, BlockEntityTicker<? super E> ticker) {
        return typeA == typeB ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityRegistry.CALIBRATED_SCULK_SHRIEKER.get(),
                CalibratedSculkShriekerBlockEntity::tick);
    }
}
