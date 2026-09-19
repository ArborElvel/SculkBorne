package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
 * 先把它藏掉，再由下面的 {@link #renderForBone} 换成原版贴图画出来。
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

    public SculkZombieTendrilLayer(GeoRenderer<SculkZombieEntity> renderer) {
        // 主贴图这一遍把触须骨骼藏掉：它的 UV 指向原版贴图，在主贴图上没有内容
        super(renderer, () -> TENDRIL_BONES, (bone, animatable, partialTick) -> {
            bone.setHidden(true);
            // setHidden 会连带隐藏子骨骼，这里放开子级，保证触须子树依然会被 renderForBone 遍历到
            bone.setChildrenHidden(false);
        });
    }

    @Override
    public void renderForBone(PoseStack poseStack, SculkZombieEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!TENDRIL_BONES.contains(bone.getName())) return;

        // 原版触须靠 mcmeta 播放动画，交给 GeckoLib 的 AnimatableTexture 按当前时间推帧
        ResourceLocation tendrilTexture = animatable.isVibrationActive()
                ? ACTIVE_TENDRIL_TEXTURE : TENDRIL_TEXTURE;
        AnimatableTexture.setAndUpdate(tendrilTexture);

        // 这根骨骼在主贴图那一遍被藏起来了，这里把它自己的方块画进原版贴图的缓冲区
        VertexConsumer tendrilBuffer = bufferSource.getBuffer(RenderType.entityCutout(tendrilTexture));
        int colour = getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt();

        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            getRenderer().renderCube(poseStack, cube, tendrilBuffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }
    }
}
