package com.unddefined.sculkborne.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import com.unddefined.sculkborne.client.model.block.SculkSkeletonHeadModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 幽匿骷髅的头的方块渲染器。
 *
 * <p>朝向按原版头颅的 16 段 {@link SkullBlock#ROTATION} 旋转；头顶的触须就画在实体贴图里，
 * 直接随模型一起渲染。
 */
public class SculkSkeletonHeadRenderer extends GeoBlockRenderer<SculkHeadBlockEntity> {
    /** 原版头颅一圈分 16 段，每段 22.5°。 */
    private static final float DEGREES_PER_ROTATION_SEGMENT = 22.5F;

    public SculkSkeletonHeadRenderer() {
        super(new SculkSkeletonHeadModel<>());
    }

    /**
     * 头用的是原版头颅的 16 段朝向，所以这里不像父类那样按四方向旋转，
     * 而是照 {@code SkullBlockRenderer} 的口径按段数转，放置朝向才和原版的头一致。
     */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        SculkHeadBlockEntity head = getAnimatable();
        BlockState state = head == null ? null : head.getBlockState();
        int rotation = state != null && state.hasProperty(SkullBlock.ROTATION) ? state.getValue(SkullBlock.ROTATION) : 0;
        poseStack.mulPose(Axis.YP.rotationDegrees(-DEGREES_PER_ROTATION_SEGMENT * rotation));
    }
}
