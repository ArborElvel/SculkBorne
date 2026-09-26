package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.entities.CreesperEntity;
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
 * 让 creesper 的 {@code whisper} 骨骼用它自己的贴图渲染。
 *
 * <p>这根骨骼的 UV 指向 {@code textures/misc/creeper_whisper.png}（带 mcmeta 的动画贴图），在主贴图上
 * 没有内容，所以主贴图那一遍交给父类 {@link FastBoneFilterGeoLayer} 把它藏掉，再由 {@link #render}
 * 换成自己的贴图画出来。
 *
 * <p>绘制放在 {@link #render}，而不是遍历到骨骼时立刻画：1.21 的 {@code MultiBufferSource} 按
 * {@link RenderType} 复用共享批次，只在自己那份 {@code lastSharedType} 结束时才 flush，在模型遍历中途
 * 换贴图会把已经攒下的主模型批次提前结束掉，之后渲染的骨骼拿到的就是已经 build 过的旧 buffer，只能
 * 指望 GeckoLib 的 {@code checkAndRefreshBuffer} 重新取一份。实体会走 outline 通道发光时
 * （{@code LevelRenderer} 把整个 {@code MultiBufferSource} 换成 {@code OutlineBufferSource}，每次取
 * buffer 都多包一层 {@code VertexMultiConsumer}），这条兜底路径不再可靠：排在后面的骨骼会直接画不出来。
 * creesper 里 {@code whisper} 虽然是 {@code body} 的最后一个子骨骼，但 {@code body} 之后还有顶层骨骼
 * 要渲染（当前顺序下是 {@code leg2}），也就是发光时会少一条腿。
 *
 * <p>所以 {@link #renderForBone} 只记下模型遍历到 {@code whisper} 时的姿势（那时的 poseStack 已经处在
 * 骨骼坐标系里），等整只模型遍历完、所有骨骼都写进主批次之后，再按记下的姿势把 {@code whisper} 画出来。
 * 这时再换 RenderType 只会结束已经写满的主批次，影响不到任何骨骼；它仍然走原来的 bufferSource，
 * 发光时照旧会进 outline 批次拿描边。
 */
public class CreesperWhisperLayer extends FastBoneFilterGeoLayer<CreesperEntity> {
    /** {@code whisper} 骨骼自己的贴图。 */
    private static final ResourceLocation WHISPER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("sculkborne", "textures/misc/creeper_whisper.png");

    /** geo.json 中使用这张贴图的骨骼名，同时是过滤与绘制的唯一依据。 */
    private static final List<String> WHISPER_BONES = List.of("whisper");

    /** whisper 骨骼这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    @Nullable
    private GeoBone whisperBone;
    @Nullable
    private Matrix4f whisperPose;
    @Nullable
    private Matrix3f whisperNormal;

    public CreesperWhisperLayer(GeoRenderer<CreesperEntity> renderer) {
        // 主贴图这一遍把 whisper 骨骼藏掉：它的 UV 指向自己的贴图，在主贴图上没有内容
        super(renderer, () -> WHISPER_BONES, (bone, animatable, partialTick) -> {
            bone.setHidden(true);
            // setHidden 会连带隐藏子骨骼，这里放开子级，保证 whisper 子树依然会被 renderForBone 遍历到
            bone.setChildrenHidden(false);
        });
    }

    @Override
    public void preRender(PoseStack poseStack, CreesperEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 父类负责把 whisper 骨骼藏掉，这里只是清掉上一帧的姿势
        super.preRender(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

        this.whisperBone = null;
        this.whisperPose = null;
        this.whisperNormal = null;
    }

    /**
     * 模型遍历到 whisper 骨骼时只记下它的姿势，真正的绘制交给 {@link #render}，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, CreesperEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!WHISPER_BONES.contains(bone.getName())) return;

        this.whisperBone = bone;
        this.whisperPose = new Matrix4f(poseStack.last().pose());
        this.whisperNormal = new Matrix3f(poseStack.last().normal());
    }

    /**
     * 整只模型画完之后再画 whisper：此时换贴图不会影响任何骨骼。
     */
    @Override
    public void render(PoseStack poseStack, CreesperEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        GeoBone whisper = this.whisperBone;
        Matrix4f pose = this.whisperPose;
        Matrix3f normal = this.whisperNormal;
        this.whisperBone = null;
        this.whisperPose = null;
        this.whisperNormal = null;

        // 主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑，也就没有姿势可用
        if (whisper == null || pose == null || normal == null || buffer == null) return;

        // 贴图带 mcmeta，交给 GeckoLib 的 AnimatableTexture 按当前时间推帧
        AnimatableTexture.setAndUpdate(WHISPER_TEXTURE);

        int colour = getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt();

        // 这根骨骼在主贴图那一遍被藏起来了，这里把它自己的方块画进自己贴图的缓冲区
        var whisperBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(WHISPER_TEXTURE));

        poseStack.pushPose();
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(normal);

        for (GeoCube cube : whisper.getCubes()) {
            poseStack.pushPose();
            getRenderer().renderCube(poseStack, cube, whisperBuffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
