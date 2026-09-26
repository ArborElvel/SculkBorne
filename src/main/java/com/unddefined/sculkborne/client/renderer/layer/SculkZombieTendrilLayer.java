package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;

import java.util.List;

/**
 * 让 sculk_zombie 头顶的触须直接用原版幽匿感测体的触须贴图渲染。
 *
 * <p>用的是 {@code textures/block/sculk_sensor_tendril_inactive.png}：16x16 大小、16 帧、
 * frametime 2 的闪烁动画。GeckoLib 会把通过 {@link net.minecraft.client.renderer.texture.TextureManager}
 * 加载的贴图换成 {@link AnimatableTexture}，所以原版这张带 mcmeta 的贴图在这里也会照原样播放动画。
 *
 * <p>geo.json 里 {@code tendril} 骨骼的 UV 已经按原版贴图的坐标改写（原版是 16x16 贴图，(4,8) 起、
 * 8x8；本模型贴图尺寸是 64x64，所以换算成 (16,32) 起、32x32），正/背面也都补齐了原版那种镜像 UV。
 * 所以主贴图那一遍要跳过这根骨骼（它在主贴图上落在空白区域）：交给父类 {@link FastBoneFilterGeoLayer}
 * 先把它藏掉，再由 {@link #render} 换成原版贴图画出来。
 *
 * <p>但触须不能在模型遍历中途借渲染器传进来的 {@link MultiBufferSource} 换 {@link RenderType}：
 * 1.21 的 {@code MultiBufferSource} 按 RenderType 复用共享批次，只在自己那份 {@code lastSharedType}
 * 结束时才 flush，中途换贴图会把已经攒下的主模型批次提前结束掉，之后渲染的骨骼拿到的就是已经
 * build 过的旧 buffer，只能指望 GeckoLib 的 {@code checkAndRefreshBuffer} 重新取一份。实体会走
 * outline 通道发光时（{@code LevelRenderer} 把整个 {@code MultiBufferSource} 换成
 * {@code OutlineBufferSource}，每次取 buffer 都多包一层 {@code VertexMultiConsumer}），这条兜底路径
 * 不再可靠：排在后面的骨骼会直接画不出来。幽匿僵尸的 {@code tendril} 挂在 {@code head} 下、渲染顺序
 * 靠前，排在它之后渲染的是 {@code right_arm} / {@code left_arm} / {@code headwear} 等部位
 * （两条腿排在 {@code body} 之前，所以不受影响），也就是发光时看到的手臂缺失。
 *
 * <p>所以这里把绘制搬到 {@link #render}：{@link #renderForBone} 只记下模型遍历到触须骨骼时的姿势
 * （那时的 poseStack 已经处在骨骼坐标系里），等整只模型遍历完、所有骨骼都写进主批次之后，再按记下的
 * 姿势把触须画出来。这时再换 RenderType 只会结束已经写满的主批次，影响不到任何骨骼；触须仍然走原来的
 * bufferSource，发光时照旧会进 outline 批次拿描边。
 */
public class SculkZombieTendrilLayer extends FastBoneFilterGeoLayer<SculkZombieEntity> {
    /** 原版幽匿感测体（未激活）的触须贴图。 */
    public static final ResourceLocation TENDRIL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/sculk_sensor_tendril_inactive.png");
    /** 原版幽匿感测体（激活中）的触须贴图。 */
    public static final ResourceLocation ACTIVE_TENDRIL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/sculk_sensor_tendril_active.png");

    /** geo.json 中使用原版触须贴图的骨骼名，同时是过滤与绘制的唯一依据。 */
    private static final List<String> TENDRIL_BONES = List.of("tendril");

    /** 触须骨骼这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    @Nullable
    private GeoBone tendrilBone;
    @Nullable
    private Matrix4f tendrilPose;
    @Nullable
    private Matrix3f tendrilNormal;

    public SculkZombieTendrilLayer(GeoRenderer<SculkZombieEntity> renderer) {
        // 主贴图这一遍把触须骨骼藏掉：它的 UV 指向原版贴图，在主贴图上没有内容
        super(renderer, () -> TENDRIL_BONES, (bone, animatable, partialTick) -> {
            bone.setHidden(true);
            // setHidden 会连带隐藏子骨骼，这里放开子级，保证触须子树依然会被 renderForBone 遍历到
            bone.setChildrenHidden(false);
        });
    }

    @Override
    public void preRender(PoseStack poseStack, SculkZombieEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 父类负责把触须骨骼藏掉，这里只是清掉上一帧的姿势
        super.preRender(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

        this.tendrilBone = null;
        this.tendrilPose = null;
        this.tendrilNormal = null;
    }

    /**
     * 模型遍历到触须骨骼时只记下它的姿势，真正的绘制交给 {@link #render}，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, SculkZombieEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!TENDRIL_BONES.contains(bone.getName())) return;

        this.tendrilBone = bone;
        this.tendrilPose = new Matrix4f(poseStack.last().pose());
        this.tendrilNormal = new Matrix3f(poseStack.last().normal());
    }

    /**
     * 整只模型画完之后再画触须：此时换 RenderType 不会影响任何骨骼，头顶的触须也能照常发光。
     */
    @Override
    public void render(PoseStack poseStack, SculkZombieEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        var tendril = this.tendrilBone;
        Matrix4f pose = this.tendrilPose;
        Matrix3f normal = this.tendrilNormal;
        this.tendrilBone = null;
        this.tendrilPose = null;
        this.tendrilNormal = null;

        // 主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑，也就没有姿势可用
        if (tendril == null || pose == null || normal == null || buffer == null) return;

        // 原版触须靠 mcmeta 播放动画，交给 GeckoLib 的 AnimatableTexture 按当前时间推帧
        ResourceLocation tendrilTexture = animatable.isVibrationActive()
                ? ACTIVE_TENDRIL_TEXTURE : TENDRIL_TEXTURE;
        AnimatableTexture.setAndUpdate(tendrilTexture);

        int colour = getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt();

        // 这根骨骼在主贴图那一遍被藏起来了，这里把它自己的方块画进原版贴图的缓冲区
        var tendrilBuffer = bufferSource.getBuffer(RenderType.entityCutout(tendrilTexture));

        poseStack.pushPose();
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(normal);

        for (GeoCube cube : tendril.getCubes()) {
            poseStack.pushPose();
            getRenderer().renderCube(poseStack, cube, tendrilBuffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
