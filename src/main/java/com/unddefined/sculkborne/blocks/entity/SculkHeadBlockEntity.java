package com.unddefined.sculkborne.blocks.entity;

import com.unddefined.sculkborne.blocks.SculkHeadBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 幽匿生物头颅的方块实体。
 *
 * <p>振动的接收、激活与红石输出全部沿用原版 {@link SculkSensorBlockEntity}，这里只把它挂到具体头颅的
 * 方块实体类型上、把激活目标换成本模组的头颅方块，并补上 GeckoLib 需要的渲染接口。
 */
public class SculkHeadBlockEntity extends SculkSensorBlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SculkHeadBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
    }

    /** 方块实体类型由方块自己登记，这里问方块要，省得每个头各写一个方块实体类。 */
    private static BlockEntityType<?> typeFor(BlockState state) {
        return ((SculkHeadBlock) state.getBlock()).blockEntityType();
    }

    /**
     * 原版 {@link VibrationUser} 收到振动后是把方块当成 {@code SculkSensorBlock} 来激活的，
     * 头方块不是感测体，所以换成这里这个版本：激活的是头自己的相位与红石状态。
     */
    @Override
    public VibrationSystem.User createVibrationUser() {
        return new HeadVibrationUser(getBlockPos());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 头的姿势是固定的，动画全部来自贴图或渲染层，不需要关键帧控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    protected class HeadVibrationUser extends VibrationUser {
        public HeadVibrationUser(BlockPos pos) {
            super(pos);
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity,
                                       @Nullable Entity playerEntity, float distance) {
            BlockState state = SculkHeadBlockEntity.this.getBlockState();
            if (!SculkHeadBlock.canActivate(state)) return;

            SculkHeadBlockEntity.this.setLastVibrationFrequency(VibrationSystem.getGameEventFrequency(gameEvent));
            int power = VibrationSystem.getRedstoneStrengthForDistance(distance, this.getListenerRadius());
            if (state.getBlock() instanceof SculkHeadBlock head) {
                head.activate(entity, level, this.blockPos, state, power,
                        SculkHeadBlockEntity.this.getLastVibrationFrequency());
            }
        }
    }
}
