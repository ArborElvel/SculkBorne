package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkSkeletonEntityModel;
import com.unddefined.sculkborne.client.renderer.layer.SculkSkeletonItemLayer;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SculkSkeletonEntityRenderer extends GeoEntityRenderer<SculkSkeletonEntity> {
    public SculkSkeletonEntityRenderer(EntityRendererProvider.Context c) {
        super(c, new SculkSkeletonEntityModel<>());
        addRenderLayer(new SculkSkeletonItemLayer(this));
    }
}
