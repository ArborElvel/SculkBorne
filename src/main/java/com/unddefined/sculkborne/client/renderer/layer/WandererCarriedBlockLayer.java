package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 把徘徊者搬起的方块画在身前。
 *
 * <p>原版是 {@code EndermanRenderer} 上的 {@code CarriedBlockLayer}，而 GeckoLib 的
 * {@code GeoEntityRenderer} 整个换掉了 {@code MobRenderer}，原版的层不会自己接上，所以这里自己挂一层。
 *
 * <p>姿势照抄原版 {@code CarriedBlockLayer}：方块离地 13 像素、身前 12 像素，绕 x 倾斜 20°、
 * 绕 y 转 45°，再缩到 0.5 倍。原版那几个数字是写在 y 朝下的 MC 模型空间里的，而 geo.json 的 y 朝上、
 * 且 x/y 都取过反，所以绕 x、绕 y 的角度与中间那段位移都取反，最后补一次 (x, y) 翻转，
 * 换算完的结果和原版完全等价（见 {@link #render}）。
 *
 * <p>挂点选 {@code body} 骨骼而不是实体原点：本模型的身体会随 CEM 动画倾斜、起伏，
 * 挂在身体上箱子才会一直贴在胸前，而不是身体动了、箱子还留在原地。
 * 这也和其它层一样，姿势在 {@link #renderForBone} 里记下、等整只模型画完再由 {@link #render} 画，
 * 中途换渲染类型不会把主模型批次提前截断（原因见 {@code SculkZombieTendrilLayer} 的注释）。
 */
public class WandererCarriedBlockLayer extends GeoRenderLayer<WandererEntity> {
    /** 方块的落点跟着身体走，挂在这根骨骼上。 */
    private static final String BODY_BONE = "body";

    /** 方块锚点：离地 13 像素，对应原版 {@code translate(0, 0.6875, -0.75)} 里的竖直分量。 */
    private static final float BLOCK_Y = 13.0F;

    /** 方块锚点：身前 12 像素，对应原版同一句里的水平分量。 */
    private static final float BLOCK_Z = -12.0F;

    /** 身体骨骼这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    @Nullable
    private Matrix4f bodyPose;
    @Nullable
    private Matrix3f bodyNormal;

    public WandererCarriedBlockLayer(GeoRenderer<WandererEntity> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 清掉上一帧的姿势
        this.bodyPose = null;
        this.bodyNormal = null;
    }

    /**
     * 模型遍历到身体时只记下它的姿势，方块交给 {@link #render} 画，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, WandererEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!BODY_BONE.equals(bone.getName())) return;

        this.bodyPose = new Matrix4f(poseStack.last().pose());
        this.bodyNormal = new Matrix3f(poseStack.last().normal());
    }

    /**
     * 整只模型画完之后再画方块：此时换渲染类型不会影响任何骨骼。
     */
    @Override
    public void render(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        Matrix4f pose = this.bodyPose;
        Matrix3f normal = this.bodyNormal;
        this.bodyPose = null;
        this.bodyNormal = null;

        BlockState state = animatable.getCarriedBlock();

        // 隐身时不画，和原版一样；主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑
        if (pose == null || normal == null || buffer == null || state == null || animatable.isInvisible()) return;

        poseStack.pushPose();
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(normal);

        // 锚点写在 body 骨骼的静止坐标系里（geo.json 的 y 朝上、-z 是正面）
        poseStack.translate(0.0F, BLOCK_Y / 16.0F, BLOCK_Z / 16.0F);
        // 原版：绕 x 倾斜 20°、绕 y 转 45°，补一段位移，0.5 倍缩放，最后绕 y 转 90°。
        // 换算到 y 朝上且 x/y 取反的 geo 坐标系后，几段角度与那段位移都取反，末尾再补回那次翻转。
        poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-45.0F));
        poseStack.translate(-0.25F, -0.1875F, 0.25F);
        poseStack.scale(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource,
                packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);

        poseStack.popPose();
    }
}
