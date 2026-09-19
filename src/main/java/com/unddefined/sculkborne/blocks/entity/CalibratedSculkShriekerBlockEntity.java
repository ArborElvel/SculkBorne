package com.unddefined.sculkborne.blocks.entity;

import com.unddefined.sculkborne.network.packet.InfrasoundParticlePacket;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import static com.unddefined.sculkborne.Config.SCULK_WHISPER_COOLDOWN;
import static com.unddefined.sculkborne.blocks.CalibratedSculkShriekerBlock.FACING;
import static com.unddefined.sculkborne.server.registry.ItemRegistry.WHISPER_DRUSE;

public class CalibratedSculkShriekerBlockEntity extends BlockEntity implements GeoBlockEntity, ContainerSingleItem.BlockContainerSingleItem {
    private static final RawAnimation itemRenderAnimation = RawAnimation.begin().thenPlay("item");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    public int cooldownTicks;

    public CalibratedSculkShriekerBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.CALIBRATED_SCULK_SHRIEKER.get(), pos, blockState);
        this.cooldownTicks = SCULK_WHISPER_COOLDOWN.get() * 20;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CalibratedSculkShriekerBlockEntity self) {
        if (!(level instanceof ServerLevel S)) return;
        if (!self.getTheItem().is(WHISPER_DRUSE)) return;
        // 更新冷却计时器
        float radius = (float) (SCULK_WHISPER_COOLDOWN.get() * 20 - self.cooldownTicks) * 0.00025f;
        if (self.cooldownTicks > 0) self.cooldownTicks--;

        var vec = switch (state.getValue(FACING)) {
            case NORTH -> pos.getCenter().add(new Vec3(0, -0.4, -0.4));
            case SOUTH -> pos.getCenter().add(new Vec3(0, -0.4, +0.4));
            case EAST  -> pos.getCenter().add(new Vec3(+0.4, -0.4, 0));
            case WEST  -> pos.getCenter().add(new Vec3(-0.4, -0.4, 0));
            case DOWN  -> pos.getCenter().add(new Vec3(0, -0.8, 0));
            default    -> pos.getCenter();
        };
        PacketDistributor.sendToAllPlayers(new InfrasoundParticlePacket(vec, radius, true));
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Activation", 0, state -> {
            if (!itemHandler.getStackInSlot(0).isEmpty())
                return state.setAndContinue(itemRenderAnimation);

            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        itemHandler.deserializeNBT(registries, tag.getCompound("ItemHandler"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("ItemHandler", itemHandler.serializeNBT(registries));
    }

    @Override
    public @NotNull BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public @NotNull ItemStack getTheItem() {
        return itemHandler.getStackInSlot(0);
    }

    @Override
    public void setTheItem(@NotNull ItemStack item) {
        itemHandler.setStackInSlot(0, item);
    }
}
