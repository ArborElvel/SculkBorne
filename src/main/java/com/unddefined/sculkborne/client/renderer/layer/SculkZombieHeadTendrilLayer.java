package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.blocks.SculkZombieHeadBlock;
import com.unddefined.sculkborne.blocks.entity.SculkZombieHeadBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;

import java.util.List;

/**
 * 用原版幽匿感测体的触须贴图渲染头顶的触须，激活时换成 active 那张。
 *
 * <p>做法与实体上的 {@code SculkZombieTendrilLayer} 相同：geo 里 {@code tendril} 骨骼的 UV 指向
 * 原版 16x16 的触须贴图（换算到 64x64 的模型贴图上是 (16,32) 起、32x32），在实体贴图上没有内容，
 * 所以主贴图那一遍先把这根骨骼藏掉、放开子骨骼，再换成原版贴图把它画出来。
 *
 * <p>贴图本身带 mcmeta 帧动画，交给 GeckoLib 的 {@link AnimatableTexture} 按当前时间推帧。
 */
public class SculkZombieHeadTendrilLayer extends FastBoneFilterGeoLayer<SculkZombieHeadBlockEntity> {
    /** 原版幽匿感测体（未激活）的触须贴图。 */
    public static final ResourceLocation TENDRIL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/sculk_sensor_tendril_inactive.png");
    /** 原版幽匿感测体（激活中）的触须贴图。 */
    public static final ResourceLocation ACTIVE_TENDRIL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/sculk_sensor_tendril_active.png");

    /** geo.json 中使用原版触须贴图的骨骼名。 */
    private static final List<String> TENDRIL_BONES = List.of("tendril");

    public SculkZombieHeadTendrilLayer(GeoRenderer<SculkZombieHeadBlockEntity> renderer) {
        // 主贴图这一遍把触须骨骼藏掉：它的 UV 指向原版贴图，在实体贴图上没有内容
        super(renderer, () -> TENDRIL_BONES, (bone, animatable, partialTick) -> {
            bone.setHidden(true);
            // setHidden 会连带隐藏子骨骼，这里放开子级，保证子树依然会被遍历到
            bone.setChildrenHidden(false);
        });
    }

    @Override
    public void renderForBone(PoseStack poseStack, SculkZombieHeadBlockEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!TENDRIL_BONES.contains(bone.getName())) return;

        // 与感测体一致：激活期用 active 贴图，冷却与待机都用 inactive
        boolean active = animatable.getBlockState().getValue(SculkZombieHeadBlock.PHASE) == SculkSensorPhase.ACTIVE;
        ResourceLocation tendrilTexture = active ? ACTIVE_TENDRIL_TEXTURE : TENDRIL_TEXTURE;
        AnimatableTexture.setAndUpdate(tendrilTexture);

        VertexConsumer tendrilBuffer = bufferSource.getBuffer(RenderType.entityCutout(tendrilTexture));
        int colour = getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt();

        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            getRenderer().renderCube(poseStack, cube, tendrilBuffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }
    }
}
