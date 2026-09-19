package com.unddefined.sculkborne.blocks.entity;

import com.unddefined.sculkborne.network.packet.InfrasoundParticlePacket;
import com.unddefined.sculkborne.server.InfrasoundDamage;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

import static com.unddefined.sculkborne.Config.*;

public class SculkWhisperBlockEntity extends BlockEntity implements GeoBlockEntity, VibrationSystem, GameEventListener.Provider<VibrationSystem.Listener> {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculkWhisperVibrationUser(this);
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

    // 添加冷却计时器
    private int cooldownTicks;

    public SculkWhisperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.SCULK_WHISPER.get(), pos, blockState);
        this.cooldownTicks = SCULK_WHISPER_COOLDOWN.get() * 20;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public VibrationSystem.@NotNull Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public @NotNull User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    public @NotNull VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SculkWhisperBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        VibrationSystem.Ticker.tick(serverLevel, blockEntity.vibrationData, blockEntity.vibrationUser);
        // 更新冷却计时器
        float radius = (float) (SCULK_WHISPER_COOLDOWN.get() * 20 - blockEntity.cooldownTicks) * 0.00025f;
        if (blockEntity.cooldownTicks > 0) blockEntity.cooldownTicks--;

        PacketDistributor.sendToAllPlayers(new InfrasoundParticlePacket(Vec3.atCenterOf(pos), radius, true));

    }

    static class SculkWhisperVibrationUser implements VibrationSystem.User {
        private final SculkWhisperBlockEntity blockEntity;
        private final PositionSource positionSource;

        public SculkWhisperVibrationUser(SculkWhisperBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
            this.positionSource = new net.minecraft.world.level.gameevent.BlockPositionSource(blockEntity.getBlockPos());
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.SHRIEKER_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
            // 只接收SHRIEKER_CAN_LISTEN标签中的游戏事件，并检查冷却状态
            return gameEvent.is(GameEventTags.SHRIEKER_CAN_LISTEN) && blockEntity.cooldownTicks <= 0;
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance) {
            Vec3 center = Vec3.atCenterOf(this.blockEntity.getBlockPos().above());
            if (this.blockEntity.cooldownTicks > 0) return;
            InfrasoundDamage.InfrasoundBurst(level, center, SCULK_WHISPER_HURT_RANGE.getAsInt(), SCULK_WHISPER_AFFECT_RANGE.getAsInt(), SCULK_WHISPER_HURT_DAMAGE.getAsInt(), entity);

            // 触发后设置冷却时间
            this.blockEntity.cooldownTicks = SCULK_WHISPER_COOLDOWN.get() * 20;
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}