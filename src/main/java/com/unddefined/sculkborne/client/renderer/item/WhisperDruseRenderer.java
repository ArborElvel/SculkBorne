package com.unddefined.sculkborne.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.client.model.item.WhisperDruseModel;
import com.unddefined.sculkborne.items.WhisperDruse;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class WhisperDruseRenderer extends GeoItemRenderer<WhisperDruse> {
    public WhisperDruseRenderer(GeoModel<WhisperDruse> M) {
        super(new WhisperDruseModel<>());
    }
    private final ResourceLocation whisper = ResourceLocation.fromNamespaceAndPath("sculkborne","textures/misc/whisper.png");

    @Override
    public void renderRecursively(PoseStack poseStack, WhisperDruse animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        if (!isReRender && bone.getName().equals("whisper")) {
            renderType = RenderType.entityTranslucent(whisper);
            buffer = bufferSource.getBuffer(renderType);
            AnimatableTexture.setAndUpdate(whisper);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,partialTick, packedLight, packedOverlay,colour);
    }
}
