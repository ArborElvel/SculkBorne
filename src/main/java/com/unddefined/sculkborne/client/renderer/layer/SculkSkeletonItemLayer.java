package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
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
 *
 * <p>但物品不能在模型遍历中途借渲染器传进来的 {@link MultiBufferSource} 画：物品渲染会换好几次
 * {@link RenderType}（本体、附魔光效、半透明层……），而 1.21 的 {@code MultiBufferSource} 按 RenderType
 * 复用共享批次，中途换类型会把已经攒下的主模型批次提前结束掉，排在 {@code right_arm} 之后渲染的骨骼
 * 拿到的就是已经 build 过的旧 buffer，只能指望 GeckoLib 的 {@code checkAndRefreshBuffer} 重新取一份。
 * 实体会走 outline 通道发光时（{@code LevelRenderer} 把整个 {@code MultiBufferSource} 换成
 * {@code OutlineBufferSource}，每次取 buffer 都多包一层 {@code VertexMultiConsumer}），这条兜底路径
 * 不再可靠，那些骨骼会直接画不出来：幽匿骷髅按当前骨骼顺序排在 {@code right_arm} 之后的是
 * {@code left_arm} 和 {@code body}，也就是发光时缺身体和左手臂。
 *
 * <p>所以这里把物品搬到 {@link #render} 里画：{@link #renderForBone} 只记下右手骨骼的姿势，等整只模型
 * 遍历完、所有骨骼都写进主批次之后再画物品。这时换 RenderType 只结束已经写满的主批次，影响不到任何
 * 骨骼；物品仍然走原来的 bufferSource，发光时照旧会进 outline 批次拿描边。
 */
public class SculkSkeletonItemLayer extends GeoRenderLayer<SculkSkeletonEntity> {

    /** 主手所在的骨骼。 */
    private static final String MAIN_HAND_BONE = "right_arm";

    /** 主手骨骼这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    @Nullable
    private Matrix4f mainHandPose;
    @Nullable
    private Matrix3f mainHandNormal;

    public SculkSkeletonItemLayer(GeoRenderer<SculkSkeletonEntity> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, SculkSkeletonEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 清掉上一帧的姿势
        this.mainHandPose = null;
        this.mainHandNormal = null;
    }

    /**
     * 模型遍历到主手骨骼时只记下它的姿势，物品交给 {@link #render} 画，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, SculkSkeletonEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!MAIN_HAND_BONE.equals(bone.getName())) return;

        this.mainHandPose = new Matrix4f(poseStack.last().pose());
        this.mainHandNormal = new Matrix3f(poseStack.last().normal());
    }

    /**
     * 整只模型画完之后再画主手物品：此时换 RenderType 不会影响任何骨骼。
     */
    @Override
    public void render(PoseStack poseStack, SculkSkeletonEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        var pose = this.mainHandPose;
        var normal = this.mainHandNormal;
        this.mainHandPose = null;
        this.mainHandNormal = null;

        // 主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑，也就没有姿势可用
        if (pose == null || normal == null || buffer == null) return;

        var stack = animatable.getMainHandItem();
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(normal);
        poseStack.translate(2.8F / 16.0F, 13.0F / 16.0F, 0.0F);
        // 与原版 ItemInHandLayer 相同的手持朝向；geo.json 的 y 朝上，绕 X 的符号与原版模型相反
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource,
                animatable.level(), packedLight, packedOverlay, animatable.getId());
        poseStack.popPose();
    }
}
