package com.unddefined.sculkborne.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.client.model.entity.CreesperEntityModel;
import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CreesperEntityRenderer extends GeoEntityRenderer<CreesperEntity> {
    public CreesperEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CreesperEntityModel<>());
    }
    private final ResourceLocation whisper = ResourceLocation.fromNamespaceAndPath("sculkborne","textures/misc/creeper_whisper.png");

    @Override
    public void renderRecursively(PoseStack poseStack, CreesperEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        if (!isReRender && bone.getName().equals("whisper")) {
            renderType = RenderType.entityTranslucent(whisper);
            buffer = bufferSource.getBuffer(renderType);
            AnimatableTexture.setAndUpdate(whisper);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,partialTick, packedLight, packedOverlay,colour);
    }
}
