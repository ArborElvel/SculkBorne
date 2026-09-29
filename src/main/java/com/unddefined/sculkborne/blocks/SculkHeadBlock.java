package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 幽匿生物头颅方块的共同部分。
 *
 * <p>方块本身和原版头颅一致：{@link SkullBlock} 的 16 段朝向、8×8×8 的形状、放置朝向、破坏掉落
 * 乃至戴在头上都沿用原版；在此基础上叠加幽匿感测体的接收与红石功能：半径 8 格接收振动、
 * 激活 30 tick 后冷却 10 tick、踩踏产生 STEP 振动、红石与比较器输出，以及激活时的尘粒与点击音效。
 * 是否共鸣相邻的振动共鸣方块由子类通过 {@link #resonatesWithAmethyst()} 决定。
 */
public abstract class SculkHeadBlock extends SkullBlock {
    /** 感测体的相位。 */
    public static final EnumProperty<SculkSensorPhase> PHASE = BlockStateProperties.SCULK_SENSOR_PHASE;
    /** 感测体的红石强度。 */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /** 激活期与冷却期时长，与原版感测体一致。 */
    public static final int ACTIVE_TICKS = 30;
    public static final int COOLDOWN_TICKS = 10;

    public boolean playSound;

    protected SculkHeadBlock(Type type, Properties properties, Boolean playSound) {
        super(type, properties);
        this.playSound = playSound;
    }

    /** 本模组为该头颅注册的方块实体类型。 */
    public abstract BlockEntityType<SculkHeadBlockEntity> blockEntityType();

    /** 激活时是否共鸣相邻的振动共鸣方块，默认与原版感测体一致。 */
    protected boolean resonatesWithAmethyst() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // 父类已经加上了头颅的 ROTATION 与 POWERED，这里补上感测体的两个状态
        super.createBlockStateDefinition(builder);
        builder.add(PHASE, POWER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SculkHeadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, blockEntityType(),
                (tickLevel, pos, tickState, blockEntity) ->
                        VibrationSystem.Ticker.tick(tickLevel, blockEntity.getVibrationData(), blockEntity.getVibrationUser()));
    }

    /** 激活期结束后进入冷却，冷却结束后回到待机，计时与原版感测体一致。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (getPhase(state) == SculkSensorPhase.ACTIVE) {
            deactivate(level, pos, state);
        } else if (getPhase(state) == SculkSensorPhase.COOLDOWN) {
            level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.INACTIVE), 3);
            if (playSound) level.playSound(null, pos, SoundEvents.SCULK_CLICKING_STOP, SoundSource.BLOCKS, 1.0F,
                    level.random.nextFloat() * 0.2F + 0.8F);
        }
    }

    /** 踩到头上会产生一次 STEP 振动，与原版感测体一样交给方块实体自己接收。 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide() && canActivate(state) && entity.getType() != EntityType.WARDEN
                && level.getBlockEntity(pos) instanceof SculkHeadBlockEntity head
                && level instanceof ServerLevel serverLevel
                && head.getVibrationUser().canReceiveVibration(serverLevel, pos, GameEvent.STEP, GameEvent.Context.of(state))) {
            head.getListener().forceScheduleVibration(serverLevel, GameEvent.STEP, GameEvent.Context.of(entity), entity.position());
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock()) && state.getValue(POWER) > 0
                && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.setBlock(pos, state.setValue(POWER, 0), 18);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) return;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (getPhase(state) == SculkSensorPhase.ACTIVE) updateNeighbours(level, pos, state);
    }

    /** 激活时按原版感测体那样喷一道幽匿转红石的尘粒，给出“正在输出”的视觉反馈。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (getPhase(state) != SculkSensorPhase.ACTIVE) return;
        Direction direction = Direction.getRandom(random);
        if (direction == Direction.UP || direction == Direction.DOWN) return;

        double x = pos.getX() + 0.5 + (direction.getStepX() == 0 ? 0.5 - random.nextDouble() : direction.getStepX() * 0.6);
        double y = pos.getY() + 0.25;
        double z = pos.getZ() + 0.5 + (direction.getStepZ() == 0 ? 0.5 - random.nextDouble() : direction.getStepZ() * 0.6);
        double speedY = random.nextFloat() * 0.04;
        level.addParticle(DustColorTransitionOptions.SCULK_TO_REDSTONE, x, y, z, 0.0, speedY, 0.0);
    }

    public static SculkSensorPhase getPhase(BlockState state) {
        return state.getValue(PHASE);
    }

    public static boolean canActivate(BlockState state) {
        return getPhase(state) == SculkSensorPhase.INACTIVE;
    }

    /** 收到振动后进入激活期：切换相位、推出红石，并按子类的设置决定要不要共鸣相邻的振动共鸣方块。 */
    public void activate(@Nullable Entity entity, Level level, BlockPos pos, BlockState state, int power, int frequency) {
        level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.ACTIVE).setValue(POWER, power), 3);
        level.scheduleTick(pos, this, ACTIVE_TICKS);
        updateNeighbours(level, pos, state);
        if (resonatesWithAmethyst()) SculkSensorBlock.tryResonateVibration(entity, level, pos, frequency);
        level.gameEvent(entity, GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, pos);
        if (playSound) level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.2F + 0.8F);
    }

    public static void deactivate(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.COOLDOWN).setValue(POWER, 0), 3);
        level.scheduleTick(pos, state.getBlock(), COOLDOWN_TICKS);
        updateNeighbours(level, pos, state);
    }

    private static void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.below(), block);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? state.getSignal(level, pos, direction) : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sensor && getPhase(state) == SculkSensorPhase.ACTIVE) {
            return sensor.getLastVibrationFrequency();
        }
        return 0;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(asItem()));
    }
}
