package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.unddefined.sculkborne.blocks.entity.CalibratedSculkShriekerBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class CalibratedSculkShriekerLayer extends BlockAndItemGeoLayer<CalibratedSculkShriekerBlockEntity> {
    public CalibratedSculkShriekerLayer(GeoRenderer<CalibratedSculkShriekerBlockEntity> renderer) {super(renderer);}

    @Override
    protected ItemStack getStackForBone(GeoBone bone, CalibratedSculkShriekerBlockEntity animatable) {
        // 只在特定骨骼上渲染物品
        if (bone.getName().equals("item")) return animatable.getTheItem();
        return null;
    }

    @Override
    protected BlockState getBlockForBone(GeoBone bone, CalibratedSculkShriekerBlockEntity animatable) {return null;}

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, CalibratedSculkShriekerBlockEntity animatable,
                                      MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        poseStack.scale(0.5f, 0.5f, 0.5f);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        // 距离小于1时不渲染物品
        if (Minecraft.getInstance().cameraEntity != null && camera.getBlockPosition().distToCenterSqr(animatable.getBlockPos().getCenter()) < 1.0)
            return;

        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
    }
}
