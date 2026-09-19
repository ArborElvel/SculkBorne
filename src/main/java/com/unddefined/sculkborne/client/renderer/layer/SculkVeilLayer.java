package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;

import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_VEIL;
import static net.minecraft.client.renderer.RenderStateShard.*;

public class SculkVeilLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SculkVeilLayer(PlayerRenderer renderer) {
        super(renderer);
    }
//TODO:未正常渲染，待修复
    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (livingEntity.hasEffect(SCULK_VEIL)) {
            var SCULK_VEIL_RENDER_TYPE = RenderType.create(
                    "shadow_veil",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    true,
                    true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(this.getTextureLocation(livingEntity), false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(RenderStateShard.OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true)
             );
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(this.getTextureLocation(livingEntity)));
            int color = FastColor.ABGR32.color(150, 255, 255, 255);

            // 渲染实体模型，使用半透明效果
            this.getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().hat.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().jacket.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().leftSleeve.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().rightSleeve.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().leftPants.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            this.getParentModel().rightPants.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
            if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
                buffers.endBatch(SCULK_VEIL_RENDER_TYPE);
            }
        }
    }
}