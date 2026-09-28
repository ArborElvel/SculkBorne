package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 徘徊者的异瞳：右眼沿用原版末影人的瞳色，左眼是 {@code #29DFEB}。
 *
 * <p>做法和原版 {@code EnderEyesLayer} 一致：主贴图脸上那两处眼睛像素保持白色，
 * 再由这一层用 {@code textures/entity/wanderer_eyes.png} 自发光覆盖上去。
 * 该贴图和主贴图同为 64x32，只有头部正面（{@code north} 面，UV {@code (8,8)-(16,16)}）的
 * 两处共 6 个像素不透明，位置与原版 {@code enderman_eyes.png} 完全相同：
 * 低 u 一侧是实体右眼，高 u 一侧是实体左眼。
 *
 * <p>因此右眼是原版末影人的 {@code E079FA / CC00FA / E079FA}，
 * 左眼是同结构的 {@code 7FECF3 / 29DFEB / 7FECF3}；把贴图里两组像素对调即可左右互换。
 *
 * <p>绘制放在 {@link #render}：1.21 的 {@code MultiBufferSource} 按 {@link RenderType} 复用共享批次，
 * 在模型遍历中途换渲染类型会把已经攒下的主模型批次提前结束掉，之后渲染的骨骼拿到的就是已经 build 过的
 * 旧 buffer，实体会走 outline 通道发光时排在后边的骨骼会直接画不出来（原因见
 * {@code SculkZombieTendrilLayer} 的注释）。所以 {@link #renderForBone} 只记下模型遍历到头部时的姿势
 * （那时的 poseStack 已经处在骨骼坐标系里），等整只模型遍历完再按记下的姿势把眼睛画出来，
 * 这样换渲染类型只结束已经写满的主批次，也照旧能跟着实体一起发光。
 *
 * <p>眼睛长在头部方块上，只重画 {@code head} 骨骼的方块就够，不必重画整只模型。
 */
public class WandererEyesLayer extends GeoRenderLayer<WandererEntity> {
    /** 眼睛贴图，只有头部正面两处共 6 个像素不透明，其余全透明。 */
    private static final ResourceLocation EYES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "textures/entity/wanderer_eyes.png");

    /** 眼睛所在的骨骼名，同时是取方块的唯一依据。 */
    private static final String HEAD_BONE = "head";

    /** 眼睛自发光，不吃光照、也不跟着实体受伤闪红，这里固定用不透明白色让贴图颜色原样输出。 */
    private static final int EYES_COLOUR = 0xFFFFFFFF;

    /** 头部骨骼这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    @Nullable
    private GeoBone headBone;
    @Nullable
    private Matrix4f headPose;
    @Nullable
    private Matrix3f headNormal;

    public WandererEyesLayer(GeoRenderer<WandererEntity> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 清掉上一帧的姿势
        this.headBone = null;
        this.headPose = null;
        this.headNormal = null;
    }

    /**
     * 模型遍历到头部时只记下它的姿势，眼睛交给 {@link #render} 画，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, WandererEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!HEAD_BONE.equals(bone.getName())) return;

        this.headBone = bone;
        this.headPose = new Matrix4f(poseStack.last().pose());
        this.headNormal = new Matrix3f(poseStack.last().normal());
    }

    /**
     * 整只模型画完之后再画眼睛：此时换渲染类型不会影响任何骨骼。
     */
    @Override
    public void render(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        GeoBone head = this.headBone;
        Matrix4f pose = this.headPose;
        Matrix3f normal = this.headNormal;
        this.headBone = null;
        this.headPose = null;
        this.headNormal = null;

        // 隐身时不画眼睛，与原版 EnderEyesLayer 一致
        if (animatable.isInvisible()) return;

        // 主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑，也就没有姿势可用
        if (head == null || pose == null || normal == null || buffer == null) return;

        var eyesBuffer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(EYES_TEXTURE));

        poseStack.pushPose();
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(normal);

        for (GeoCube cube : head.getCubes()) {
            poseStack.pushPose();
            getRenderer().renderCube(poseStack, cube, eyesBuffer, packedLight, packedOverlay, EYES_COLOUR);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
