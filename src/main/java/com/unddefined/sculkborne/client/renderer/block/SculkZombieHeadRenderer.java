package com.unddefined.sculkborne.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.blocks.entity.SculkZombieHeadBlockEntity;
import com.unddefined.sculkborne.client.model.block.SculkZombieHeadModel;
import com.unddefined.sculkborne.client.renderer.layer.SculkZombieHeadTendrilLayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 幽匿僵尸的头的方块渲染器。
 *
 * <p>朝向按原版头颅的 16 段 {@link SkullBlock#ROTATION} 旋转；触须交给 {@link SculkZombieHeadTendrilLayer}
 * 用原版幽匿感测体的触须贴图单独画。
 */
public class SculkZombieHeadRenderer extends GeoBlockRenderer<SculkZombieHeadBlockEntity> {
    /** 原版头颅一圈分 16 段，每段 22.5°。 */
    private static final float DEGREES_PER_ROTATION_SEGMENT = 22.5F;

    public SculkZombieHeadRenderer() {
        super(new SculkZombieHeadModel<>());
        addRenderLayer(new SculkZombieHeadTendrilLayer(this));
    }

    /**
     * 头用的是原版头颅的 16 段朝向，所以这里不像父类那样按四方向旋转，
     * 而是照 {@code SkullBlockRenderer} 的口径按段数转，放置朝向才和原版的头一致。
     */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        SculkZombieHeadBlockEntity head = getAnimatable();
        BlockState state = head == null ? null : head.getBlockState();
        int rotation = state != null && state.hasProperty(SkullBlock.ROTATION) ? state.getValue(SkullBlock.ROTATION) : 0;
        poseStack.mulPose(Axis.YP.rotationDegrees(-DEGREES_PER_ROTATION_SEGMENT * rotation));
    }
}
