package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.blocks.entity.SculkWhisperBlockEntity;
import com.unddefined.sculkborne.server.InfrasoundDamage;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.unddefined.sculkborne.Config.*;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_INTRUSION;

public class SculkWhisperBlock extends Block implements EntityBlock {
    public SculkWhisperBlock() {
        super(Properties.of()
                .strength(0.5F)
                .sound(SoundType.SCULK_SHRIEKER)
                .lightLevel(state -> 2)
                .noOcclusion()
                .noLootTable()
        );
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        // 方块转换
        if (stack.is(ItemRegistry.ECHO_DRUSE.get())) {
            var itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ItemRegistry.WHISPER_DRUSE.toStack());
            level.addFreshEntity(itemEntity);
            level.setBlock(pos, Blocks.SCULK_SHRIEKER.defaultBlockState(), 3);
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof LivingEntity e)) return;
        e.addEffect(new MobEffectInstance(SCULK_INTRUSION, 20 * 60));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SculkWhisperBlockEntity(pos, state);
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityRegistry.SCULK_WHISPER.get(), SculkWhisperBlockEntity::tick);
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> typeA, BlockEntityType<E> typeB, BlockEntityTicker<? super E> ticker) {
        return typeA == typeB ? (BlockEntityTicker<A>) ticker : null;
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if ( level instanceof ServerLevel serverLevel)
            InfrasoundDamage.InfrasoundBurst(serverLevel, pos.above().getCenter(), SCULK_WHISPER_HURT_RANGE.get() / 1.5f, SCULK_WHISPER_AFFECT_RANGE.get() / 1.5f, SCULK_WHISPER_HURT_DAMAGE.get() / 2,null);
    }
}