package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 把幽匿骷髅主手的物品（默认是弓）画在右手手臂上。
 *
 * <p>GeckoLib 的 {@code GeoEntityRenderer} 不会像原版 {@code ItemInHandLayer} 那样自动渲染手持物，
 * 需要在骨骼上自己挂一层。{@link #renderForBone} 拿到的 poseStack 已经处在骨骼的坐标系里
 * （geo.json 的坐标：x 取反、y 朝上），父级骨骼的旋转与位移都在其中，所以只要把手掌在 geo.json 里的
 * 绝对坐标写进 {@code translate}，物品就会跟着手臂一起动。
 */
public class SculkSkeletonItemLayer extends GeoRenderLayer<SculkSkeletonEntity> {

    /** 主手所在的骨骼。 */
    private static final String MAIN_HAND_BONE = "right_arm";

    public SculkSkeletonItemLayer(GeoRenderer<SculkSkeletonEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, SculkSkeletonEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!MAIN_HAND_BONE.equals(bone.getName())) return;

        ItemStack stack = animatable.getMainHandItem();
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(2.8F / 16.0F, 13.0F / 16.0F, 0.0F);
        // 与原版 ItemInHandLayer 相同的手持朝向；geo.json 的 y 朝上，绕 X 的符号与原版模型相反
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource,
                animatable.level(), packedLight, packedOverlay, animatable.getId());
        poseStack.popPose();

        // 物品渲染换过 buffer，还原一下再交给后续骨骼
        bufferSource.getBuffer(renderType);
    }
}
